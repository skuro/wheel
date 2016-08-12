(ns rill.wheel.registration-flow-example-test
  "This test demonstrates something close to a standard user
  registration flow with email validation."
  (:require [clojure.test :refer [deftest is testing]]
            [rill.wheel
             [aggregate :as aggregate :refer [defaggregate defevent]]
             [command :as command :refer [defcommand]]
             [repository :as repo]
             [testing :refer [ephemeral-repository sub?]]]))

;;;;
;;;; Let's define an "account" first so we have a starting point for
;;;; our story.
;;;;

(defaggregate account
  "An account entity tracks the status of a user account with a given
  id. In this example all we care about is whether an account exists
  at all."
  [account-id]
  {:pre [(keyword? account-id)]}) ; typically, we'd use a UUID or something

;;;;
;;;; An account can be registered and that's pretty much it
;;;;

(defcommand register-account
  "Register an account with the given name. The account-id is
  generated somewhere else and should be unique."
  {::command/events [::account-registered]}
  [repo account-id full-name]
  (let [account (account repo account-id)]
    (if (aggregate/exists account)
      (command/rejection account "Account with id already exists")
      (account-registered account full-name))))

(defevent account-registered
  "Someone registered an account with a name"
  [account full-name]
  account)

;;;;
;;;; After an account is registered, we would like the user to have a
;;;; confirmed email address.
;;;;
;;;; The user is expected to enter her email address in a form, after
;;;; which we'll send an email to that adress containing a secret
;;;; token that should be provided back to the application to prove
;;;; that the user is in control of that address.
;;;;
;;;; Until the user proves her control of the address ownership of the
;;;; address can be in dispute; multiple accounts can try to claim the
;;;; address.
;;;;

(defaggregate email-ownership
  "An email-ownership entity holds the claims to a given email-address.

Ownership can be unclaimed, claimed by one or more accounts (possibly
multiple times), or taken by a single account.

When ownership is taken, it cannot be claimed or owned by other
accounts."
  [email-address]
  {:pre [(string? email-address)]})

(defn random-secret
  []
  (java.util.UUID/randomUUID))

(defcommand claim-email-address
  "Account with claiming-account-id wants to register email-address.

This will fail if email adress is already taken by some other
account. Otherwise the email address needs to be confirmed within some
amount of time."
  {::command/events [::email-address-claimed]}
  [repo claiming-account-id email-address]
  (let [account (account repo claiming-account-id)]
    (if (aggregate/exists account)
      (let [{current-owner :account-id :as ownership} (email-ownership repo email-address)]
        (if (and current-owner (not= claiming-account-id (:account-id ownership)))
          (command/rejection ownership "Email address is already taken")
          (email-address-claimed ownership claiming-account-id (random-secret))))
      (command/rejection account "No such account"))))

(defevent email-address-claimed
  "There is a provisional claim on this email address that can be
  confirmed with secret.

Note that it's possible to have multiple concurrent claims on the same
and other accounts until one claim is confirmed!"
  [email-ownership account-id secret]
  (update-in email-ownership [:claims account-id] (fnil conj #{}) secret))

;;;;
;;;; We don't want to give people forever to claim their
;;;; address. After some reasonable time their confirmation attempt
;;;; will expire.
;;;;

(defcommand expire-email-address-confirmation
  " We don't want to give people forever to claim their address. After
some reasonable amount of time their confirmation attempt will expire.

This command expires an email confirmation attempt. In a production
environment this might be called by a background worker."
  {::command/events [::email-address-confirmation-expired]}
  [repo account-id email-address secret]
  (let [ownership (email-ownership repo email-address)]
    (if (aggregate/exists ownership)
      (email-address-confirmation-expired ownership account-id secret)
      (command/rejection ownership "No activity on this email address"))))

(defevent email-address-confirmation-expired
  "It's taken too long to confirm this email address"
  [email-ownership account-id secret]
  (update-in email-ownership [:claims account-id] disj secret))

;;;;
;;;; When the user receives her secret, she can confirm control of the
;;;; address.
;;;;

(defcommand confirm-email-address
  "Account attempts to confirm ownership of email address. This will
  fail if the token is incorrect, the email address was never claimed
  by this account or if another account confirmed the address
  earlier."
  {::command/events [::email-address-taken]}
  [repo account-id email-address secret]
  (if-let [ownership (aggregate/exists (email-ownership repo email-address))]
    (if-let [current-owner-id (:account-id ownership)]
      (if (= current-owner-id account-id)
        ownership ; already owned by account-id
        (command/rejection ownership "Email address already owned by other account"))
      (if (get-in ownership [:claims account-id secret])
        (email-address-taken ownership account-id)
        (command/rejection ownership "Invalid confirmation")))
    (command/rejection nil "Email address was never claimed")))

(defevent email-address-taken
  "This email address was confirmed"
  [email-ownership account-id]
  (-> email-ownership
      (dissoc :claims)                  ; remove any pending claims
      (assoc :account-id account-id)))

(defevent email-address-released
  "There is no current owner of this address"
  [email-ownership]
  (dissoc email-ownership :account-id))

;;;;
;;;; Some test helpers
;;;;

(defn test-repo-with-two-users
  "Initialize a new repository with two registered users, neither of
  whom have a registered email address."
  []
  (doto (ephemeral-repository)
    (register-account :first-user "User Number One")
    (register-account :second-user "User Number Two")))

(defn secret-from-claim-result
  "Get secret token from result of executing `claim-email-address`"

  ;; The first (and only) event resulting from a successful
  ;; `claim-email-address` command is a `email-address-claimed`,
  ;; with :secret property that we need to `confirm-email-address`
  ;; in a real world scenario, this secret would be mailed to the
  ;; user by some process that's watching for
  ;; ::email-address-claimed events. In this test scenario, we'll
  ;; just extract the secret from the event and pass it along
  ;; here.

  [result-of-claim]
  ;; assert that claim was actually accepted
  (is (sub? {::command/status :ok
             ::command/events [{:rill.message/type ::email-address-claimed}]}
            result-of-claim))
  (get-in result-of-claim [::command/events 0 :secret]))


;;;;
;;;; Let's show that all of this actually works
;;;;
;;;; First, happy flow
;;;;

(deftest test-happy-flow
  (testing "User registers email address"
    (let [repo   (test-repo-with-two-users)
          secret (-> repo
                     (claim-email-address :first-user "one@example.com")
                     secret-from-claim-result)]
      (is secret)
      (is (sub? {::command/status :ok
                 ::command/events [{:rill.message/type ::email-address-taken
                                    :account-id        :first-user}]}
                (confirm-email-address repo :first-user "one@example.com" secret))))))

;;;;
;;;; Show that some of the unhappy flows are implemented
;;;;

(deftest test-unhappy-flows
  ;; Happy flows are all alike; every unhappy flow is unhappy in its
  ;; own way.
  (testing "Multiple users attempting to register same, untaken address"
    (let [repo    (test-repo-with-two-users)
          secret1 (-> repo
                      (claim-email-address :first-user "example@example.com")
                      secret-from-claim-result)
          secret2 (-> repo
                      (claim-email-address :second-user "example@example.com")
                      secret-from-claim-result)]
      (is secret1 "secret generated for claim 1")
      (is secret2 "secret generated for claim 2")
      (is (not= secret1 secret2)
          "secrets are different for each claim")
      (is (sub? {::command/status :ok
                 ::command/events [{:rill.message/type ::email-address-taken
                                    :account-id        :second-user}]}
                (confirm-email-address repo :second-user "example@example.com" secret2))
          "first confirmation succeeds")

      (is (command/rejection? (confirm-email-address repo :first-user "example@example.com" secret1))
          "second confirmation is rejected")))

  (testing "User attempting to register already taken address"
    (let [repo (test-repo-with-two-users)

          ;; complete registering the address with :first-user
          secret (-> repo
                     (claim-email-address :first-user "example@example.com")
                     secret-from-claim-result)]
      (is secret "secret generated for first claim")
      (is (sub? {::command/status :ok
                 ::command/events [{:rill.message/type ::email-address-taken
                                    :account-id        :first-user}]}
                (confirm-email-address repo :first-user "example@example.com" secret))
          "first confirmation succeeds")
      ;; email address is now owned by :first-user
      (is (command/rejection? (claim-email-address repo :second-user "example@example.com"))
          "second claim is rejected immediately")))

  (testing "Malicious attempt to register address with non-existing user"
    (let [repo (ephemeral-repository)]
      (is (command/rejection? (claim-email-address repo :some-other-user "some.address@example.com")))))

  (testing "Malicious attempt to register address with incorrect token"
    (let [repo   (test-repo-with-two-users)
          secret (-> repo
                     (claim-email-address :first-user "one@example.com")
                     secret-from-claim-result)]
      (is (command/rejection? (confirm-email-address repo :first-user "one@example.com" (random-secret)))
          "Guessing a secret will not work")))

  (testing "User is too late to confirm registration"
    (let [repo   (test-repo-with-two-users)
          secret (-> repo
                     (claim-email-address :first-user "one@example.com")
                     secret-from-claim-result)]
      (is secret "Secret received")
      (is (command/ok? (expire-email-address-confirmation repo :first-user "one@example.com" secret))
          "Timeout committed")
      (is (command/rejection? (confirm-email-address repo :first-user "one@example.com" secret))))))