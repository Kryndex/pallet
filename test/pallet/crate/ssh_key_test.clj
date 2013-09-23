(ns pallet.crate.ssh-key-test
  (:require
   [clojure.test :refer :all]
   [clojure.tools.logging :as logging]
   [pallet.actions :refer [directory exec-checked-script file remote-file user]]
   [pallet.api :refer [group-spec lift plan-fn]]
   [pallet.build-actions :as build-actions]
   [pallet.common.logging.logutils :refer [logging-threshold-fixture]]
   [pallet.context :as context]
   [pallet.core.api :refer [phase-errors]]
   [pallet.core.user :refer [*admin-user*]]
   [pallet.crate.ssh-key :refer :all]
   [pallet.live-test :as live-test]
   [pallet.script.lib :as lib]
   [pallet.stevedore :as stevedore]
   [pallet.test-utils
    :refer [make-localhost-compute
            no-location-info
            test-username
            with-ubuntu-script-template]]
   [pallet.utils :refer [with-temp-file]]))

(use-fixtures
 :once
 with-ubuntu-script-template
 (logging-threshold-fixture)
 no-location-info)

(defn- local-test-user
  []
  (assoc *admin-user* :username (test-username) :no-sudo true))

(deftest authorize-key-test
  (is (script-no-comment=
       (first
        (context/with-phase-context
          {:kw :authorize-key :msg "authorize-key"}
          (build-actions/build-actions
           {}
           (directory
            "$(getent passwd fred | cut -d: -f6)/.ssh/"
            :owner "fred" :mode "755")
           (file
            "$(getent passwd fred | cut -d: -f6)/.ssh/authorized_keys"
            :owner "fred" :mode "644")
           (exec-checked-script
            "authorize-key on user fred"
            (var auth_file
                 "$(getent passwd fred | cut -d: -f6)/.ssh/authorized_keys")
            (if-not ("fgrep" (quoted "key1") @auth_file)
              (println (quoted "key1") ">>" @auth_file)))
           (exec-checked-script
            "Set selinux permissions"
            (~lib/selinux-file-type
             "$(getent passwd fred | cut -d: -f6)/.ssh/" "user_home_t")))))
       (first
        (build-actions/build-actions
         {}
         (authorize-key "fred" "key1"))))))

(deftest install-key-test
  (is (script-no-comment=
       (first
        (context/with-phase-context
          {:kw :install-key :msg "install-key"}
          (build-actions/build-actions
           {}
           (directory
            "$(getent passwd fred | cut -d: -f6)/.ssh/"
            :owner "fred" :mode "755")
           (remote-file
            "$(getent passwd fred | cut -d: -f6)/.ssh/id"
            :content "private" :owner "fred" :mode "600")
           (remote-file
            "$(getent passwd fred | cut -d: -f6)/.ssh/id.pub"
            :content "public" :owner "fred" :mode "644"))))
       (first
        (build-actions/build-actions
         {} (install-key "fred" "id" "private" "public")))))
  (is (script-no-comment=
       (first
        (context/with-phase-context
          {:kw :install-key :msg "install-key"}
          (build-actions/build-actions
           {}
           (directory
            "$(getent passwd fred | cut -d: -f6)/.ssh/"
            :owner "fred" :mode "755")
           (remote-file
            "$(getent passwd fred | cut -d: -f6)/.ssh/id"
            :content "private" :owner "fred" :mode "600")
           (remote-file
            "$(getent passwd fred | cut -d: -f6)/.ssh/id.pub"
            :content "public" :owner "fred" :mode "644"))))
       (first
        (build-actions/build-actions
         {}
         (install-key "fred" "id" "private" "public"))))))

(deftest generate-key-test
  (is (script-no-comment=
       (first
        (build-actions/build-actions
         {:phase-context "generate-key"}
         (directory
          "$(getent passwd fred | cut -d: -f6)/.ssh"
          :owner "fred" :mode "755")
         (exec-checked-script
          "ssh-keygen"
          (var key_path "$(getent passwd fred | cut -d: -f6)/.ssh/id_rsa")
          (if-not (file-exists? @key_path)
            ("ssh-keygen"
             ~(stevedore/map-to-arg-string
               {:f (stevedore/script @key_path) :t "rsa" :N ""
                :C "generated by pallet"}))))
         (file
          "$(getent passwd fred | cut -d: -f6)/.ssh/id_rsa"
          :owner "fred" :mode "0600")
         (file
          "$(getent passwd fred | cut -d: -f6)/.ssh/id_rsa.pub"
          :owner "fred" :mode "0644")))
       (first
        (build-actions/build-actions
         {}
         (generate-key "fred")))))

  (is (script-no-comment=
       (first
        (pallet.context/with-phase-context
          {:kw :generate-key :msg "generate-key"}
          (build-actions/build-actions
           {}
           (directory
            "$(getent passwd fred | cut -d: -f6)/.ssh"
            :owner "fred" :mode "755")
           (exec-checked-script
            "ssh-keygen"
            (var key_path "$(getent passwd fred | cut -d: -f6)/.ssh/id_dsa")
            (if-not (file-exists? @key_path)
              ("ssh-keygen"
               ~(stevedore/map-to-arg-string
                 {:f (stevedore/script @key_path) :t "dsa" :N ""
                  :C "generated by pallet"}))))
           (file
            "$(getent passwd fred | cut -d: -f6)/.ssh/id_dsa"
            :owner "fred" :mode "0600")
           (file
            "$(getent passwd fred | cut -d: -f6)/.ssh/id_dsa.pub"
            :owner "fred" :mode "0644"))))
       (first
        (build-actions/build-actions
         {} (generate-key "fred" :type "dsa")))))

  (is (script-no-comment=
       (first
        (pallet.context/with-phase-context
          {:kw :generate-key :msg "generate-key"}
          (build-actions/build-actions
           {}
           (directory
            "$(getent passwd fred | cut -d: -f6)/.ssh"
            :owner "fred" :mode "755")
           (exec-checked-script
            "ssh-keygen"
            (var key_path "$(getent passwd fred | cut -d: -f6)/.ssh/identity")
            (if-not (file-exists? @key_path)
              ("ssh-keygen"
               ~(stevedore/map-to-arg-string
                 {:f (stevedore/script @key_path) :t "rsa1" :N ""
                  :C "generated by pallet"}))))
           (file
            "$(getent passwd fred | cut -d: -f6)/.ssh/identity"
            :owner "fred" :mode "0600")
           (file
            "$(getent passwd fred | cut -d: -f6)/.ssh/identity.pub"
            :owner "fred" :mode "0644"))))
       (first
        (build-actions/build-actions
         {} (generate-key "fred" :type "rsa1")))))

  (is (script-no-comment=
       (first
        (build-actions/build-actions
         {:phase-context "generate-key"}
         (exec-checked-script
          "ssh-keygen"
          (var key_path "$(getent passwd fred | cut -d: -f6)/.ssh/c")
          (if-not (file-exists? @key_path)
            ("ssh-keygen"
             ~(stevedore/map-to-arg-string
               {:f (stevedore/script @key_path)
                :t "rsa1" :N "abc"  :C "my comment"}))))
         (file "$(getent passwd fred | cut -d: -f6)/.ssh/c"
               :owner "fred" :mode "0600")
         (file "$(getent passwd fred | cut -d: -f6)/.ssh/c.pub"
               :owner "fred" :mode "0644")))
       (first
        (build-actions/build-actions
         {}
         (generate-key
          "fred" :type "rsa1" :filename "c" :no-dir true
          :comment "my comment" :passphrase "abc"))))))

(deftest authorize-key-for-localhost-test
  (is (script-no-comment=
       (first
        (pallet.context/with-phase-context
          {:kw :generate-key :msg "authorize-key-for-localhost"}
          (build-actions/build-actions
           {}
           (directory
            "$(getent passwd fred | cut -d: -f6)/.ssh/"
            :owner "fred" :mode "755")
           (file
            "$(getent passwd fred | cut -d: -f6)/.ssh/authorized_keys"
            :owner "fred" :mode "644")
           (exec-checked-script
            "authorize-key"
            (var key_file
                 "$(getent passwd fred | cut -d: -f6)/.ssh/id_dsa.pub")
            (var auth_file
                 "$(getent passwd fred | cut -d: -f6)/.ssh/authorized_keys")
            (if-not ("grep" (quoted @("cat" @key_file)) @auth_file)
              (do
                (print (quoted "from=\\\"localhost\\\" ") ">>" @auth_file)
                ("cat" @key_file ">>" @auth_file)))))))
       (first
        (build-actions/build-actions
         {}
         (authorize-key-for-localhost "fred" "id_dsa.pub")))))

  (is (script-no-comment=
       (first
        (pallet.context/with-phase-context
          {:kw :generate-key :msg "authorize-key-for-localhost"}
          (build-actions/build-actions
           {}
           (directory
            "$(getent passwd tom | cut -d: -f6)/.ssh/"
            :owner "tom" :mode "755")
           (file
            "$(getent passwd tom | cut -d: -f6)/.ssh/authorized_keys"
            :owner "tom" :mode "644")
           (exec-checked-script
            "authorize-key"
            (var key_file
                 "$(getent passwd fred | cut -d: -f6)/.ssh/id_dsa.pub")
            (var auth_file
                 "$(getent passwd tom | cut -d: -f6)/.ssh/authorized_keys")
            (if-not ("grep" (quoted @("cat" @key_file)) @auth_file)
              (do
                (print (quoted "from=\\\"localhost\\\" ") ">>" @auth_file)
                ("cat" @key_file ">>" @auth_file)))))))
       (first
        (build-actions/build-actions
         {}
         (authorize-key-for-localhost
          "fred" "id_dsa.pub" :authorize-for-user "tom"))))))

(deftest invoke-test
  (is (build-actions/build-actions
       {}
       (authorize-key "user" "pk")
       (authorize-key-for-localhost "user" "pk")
       (install-key "user" "name" "pk" "pubk")
       (generate-key "user"))))

(defn check-public-key
  [key]
  (fn [session]
    (logging/trace (format "check-public-key session is %s" session))
    (logging/debug (format "check-public-key key is %s" key))
    (is (string? key))
    [key session]))

(deftest config-test
  (with-temp-file [tmp ""]
    (let [compute (make-localhost-compute :group-name "local")
          op (lift
              (group-spec "local")
              :phase (plan-fn
                       (config "github.com" {"StrictHostKeyChecking" "no"}
                               :config-file (.getPath tmp))
                       (config "somewhere" {"StrictHostKeyChecking" "no"}
                               :config-file (.getPath tmp))
                       (config "github.com" {"StrictHostKeyChecking" "yes"}
                               :config-file (.getPath tmp)))
              :compute compute
              :user (local-test-user)
              :async true)
          session @op]
      (is (not (phase-errors @op)))
      (is (= "Host somewhere\n  StrictHostKeyChecking = no\nHost github.com\n  StrictHostKeyChecking = yes\n"
             (slurp tmp))))))

(deftest live-test
  (live-test/test-for
   [image live-test/*images*]
   (let [automated-admin-user
         (var-get
          (resolve 'pallet.crate.automated-admin-user/automated-admin-user))]
     (live-test/test-nodes
      [compute node-map node-types]
      {:ssh-key
       {:image image
        :count 1
        :phases
        {:bootstrap (plan-fn
                     (automated-admin-user)
                     (user "testuser"))
         :configure (plan-fn (generate-key "testuser"))
         :verify1 (plan-fn
                    (public-key "testuser"))
         :verify2 (plan-fn
                   (check-public-key))}}}
      (lift (:ssh-key node-types)
                 :phase [:verify1 :verify2]
                 :compute compute)))))
