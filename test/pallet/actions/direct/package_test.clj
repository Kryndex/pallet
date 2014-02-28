(ns pallet.actions.direct.package-test
  (:require
   [clojure.java.io :as io]
   [clojure.test :refer :all]
   [pallet.action :refer [action-fn]]
   [pallet.actions.decl :refer [remote-file]]
   [pallet.actions.impl :refer [checked-commands]]
   [pallet.actions.direct.package
    :refer [add-scope* adjust-packages package* packages*
            package-manager* package-source*]]
   [pallet.actions.direct.file :refer [sed*]]
   [pallet.actions.direct.remote-file :refer [remote-file*]]
   [pallet.build-actions
    :refer [build-actions build-script build-session centos-session
            ubuntu-session]]
   [pallet.common.logging.logutils :refer [logging-threshold-fixture]]
   [pallet.group :refer [group-spec]]
   [pallet.plan :refer [plan-context]]
   [pallet.local.execute :as local]
   [pallet.script :as script]
   [pallet.script :refer [with-script-context]]
   [pallet.script.lib :as lib]
   [pallet.stevedore :as stevedore]
   [pallet.test-utils :as test-utils]))

(use-fixtures
 :each
 test-utils/with-ubuntu-script-template
 test-utils/with-bash-script-language
 test-utils/with-no-source-line-comments
 test-utils/no-location-info)

(use-fixtures
 :once
 (logging-threshold-fixture))

(def action-state {:options {:user {:username "fred" :password "x"}}})
(def action-options (:options action-state))

(defn package
  "A helper to remove duplication"
  [name options]
  (second (package* action-state name options)))

(deftest test-install-example
  (testing "apt"
    (with-script-context [:ubuntu :apt]
      (testing "package"
        (testing "default action"
          (is (script-no-comment=
               (stevedore/checked-script
                "Packages"
                (lib/package-manager-non-interactive)
                (chain-and
                 (defn enableStart [] (lib/rm "/usr/sbin/policy-rc.d"))
                 "apt-get -q -y install rubygems+"))
               (package "rubygems" {:packager :apt}))))
        (testing "explicit install"
          (is (script-no-comment=
               (stevedore/checked-script
                "Packages"
                (lib/package-manager-non-interactive)
                (chain-and
                 (defn enableStart [] (lib/rm "/usr/sbin/policy-rc.d"))
                 "apt-get -q -y install java+"))
               (package "java" {:action :install :packager :apt}))))
        (testing "remove"
          (is (script-no-comment=
               (stevedore/checked-script
                "Packages"
                (lib/package-manager-non-interactive)
                (chain-and
                 (defn enableStart [] (lib/rm "/usr/sbin/policy-rc.d"))
                 "apt-get -q -y install git-"))
               (package "git" {:action :remove :packager :apt}))))
        (testing "purge"
          (is (script-no-comment=
               (stevedore/checked-script
                "Packages"
                (lib/package-manager-non-interactive)
                (chain-and
                 (defn enableStart [] (lib/rm "/usr/sbin/policy-rc.d"))
                 "apt-get -q -y install ruby_"))
               (package "ruby"
                        {:action :remove :purge true :packager :apt})))))))

  (testing "aptitude"
    (with-script-context [:ubuntu :aptitude]
      (testing "package"
        (is (script-no-comment=
             (stevedore/checked-script
              "Packages"
              (~lib/package-manager-non-interactive)
              (defn enableStart [] (lib/rm "/usr/sbin/policy-rc.d"))
              "aptitude install -q -y java+"
              "aptitude search --disable-columns \"?and(?installed, ?name(^java$))\" | grep \"java\"")
             (package "java" {:action :install :packager :aptitude})))
        (is (script-no-comment=
             (stevedore/checked-script
              "Packages"
              (~lib/package-manager-non-interactive)
              (defn enableStart [] (lib/rm "/usr/sbin/policy-rc.d"))
              "aptitude install -q -y rubygems+"
              "aptitude search --disable-columns \"?and(?installed, ?name(^rubygems$))\" | grep \"rubygems\"")
             (package "rubygems" {:packager :aptitude})))
        (is (script-no-comment=
             (stevedore/checked-script
              "Packages"
              (~lib/package-manager-non-interactive)
              (defn enableStart [] (lib/rm "/usr/sbin/policy-rc.d"))
              "aptitude install -q -y git-"
              "! { aptitude search --disable-columns \"?and(?installed, ?name(^git$))\" | grep \"git\"; }")
             (package "git" {:action :remove :packager :aptitude})))
        (is (script-no-comment=
             (stevedore/checked-script
              "Packages"
              (~lib/package-manager-non-interactive)
              (defn enableStart [] (lib/rm "/usr/sbin/policy-rc.d"))
              "aptitude install -q -y ruby_"
              "! { aptitude search --disable-columns \"?and(?installed, ?name(^ruby$))\" | grep \"ruby\"; }")
             (package "ruby"
                      {:action :remove :purge true :packager :aptitude}))))))

  (testing "yum"
    (with-script-context [:centos :yum]
      (testing "package"
        (is (script-no-comment=
             (stevedore/checked-script
              "Packages"
              "yum install -q -y java"
              ("yum" list installed))
             (package "java" {:action :install :packager :yum})))
        (is (script-no-comment=
             (stevedore/checked-script
              "Packages"
              "yum install -q -y rubygems"
              ("yum" list installed))
             (package "rubygems" {:packager :yum})))
        (is (script-no-comment=
             (stevedore/checked-script
              "Packages"
              "yum upgrade -q -y maven2"
              ("yum" list installed))
             (package "maven2" {:action :upgrade :packager :yum})))
        (is (script-no-comment=
             (stevedore/checked-script
              "Packages"
              "yum remove -q -y git"
              ("yum" list installed))
             (package "git" {:action :remove :packager :yum})))
        (is (script-no-comment=
             (stevedore/checked-script
              "Packages"
              "yum remove -q -y ruby"
              ("yum" list installed))
             (package "ruby" {:action :remove :purge true :packager :yum}))))))

  (testing "pacman"
    (with-script-context [:arch :pacman]
      (is (script-no-comment=
           (stevedore/checked-script
            "Packages"
            "pacman -S --noconfirm --noprogressbar java")
           (package "java" {:action :install :packager :pacman})))
      (is (script-no-comment=
           (stevedore/checked-script
            "Packages"
            ("pacman -S --noconfirm --noprogressbar rubygems"))
           (package "rubygems" {:packager :pacman})))
      (is (script-no-comment=
           (stevedore/checked-script
            "Packages"
            "pacman -S --noconfirm --noprogressbar maven2")
           (package "maven2" {:action :upgrade :packager :pacman})))
      (is (script-no-comment=
           (stevedore/checked-script
            "Packages"
            ("pacman -R --noconfirm git"))
           (package "git" {:action :remove :packager :pacman})))
      (is (script-no-comment=
           (stevedore/checked-script
            "Packages"
            "pacman -R --noconfirm --nosave ruby")
           (package "ruby"
                    {:action :remove :purge true :packager :pacman}))))))

(deftest package-manager-non-interactive-test
  (is (script-no-comment=
       "{ debconf-set-selections <<EOF
debconf debconf/frontend select noninteractive
debconf debconf/frontend seen false
EOF
}"
       (script/with-script-context [:aptitude]
         (stevedore/script (~lib/package-manager-non-interactive))))))

(deftest add-scope-test
  (is (script-no-comment=
       (stevedore/chained-script
        (set! tmpfile @("mktemp" -t addscopeXXXX))
        ("cp" -p "/etc/apt/sources.list" @tmpfile)
        ("awk"
         "'{if ($1 ~ /^deb/ && ! /multiverse/  ) print $0 \" \" \" multiverse \" ; else print; }'"
         "/etc/apt/sources.list" > @tmpfile)
        ("mv" -f @tmpfile "/etc/apt/sources.list"))
       (add-scope* "deb" "multiverse" "/etc/apt/sources.list")))

  (testing "with sources.list"
    (let [tmp (java.io.File/createTempFile "package_test" "test")]
      (io/copy "deb http://archive.ubuntu.com/ubuntu/ karmic main restricted
deb-src http://archive.ubuntu.com/ubuntu/ karmic main restricted"
               tmp)
      (is (=
           {:exit 0, :out "", :err ""}
           (local/local-script
            ~(add-scope* "deb" "multiverse" (.getPath tmp)))))
      (is
       (=
        (str "deb http://archive.ubuntu.com/ubuntu/ karmic main restricted  "
             "multiverse \ndeb-src http://archive.ubuntu.com/ubuntu/ karmic "
             "main restricted  multiverse \n")
        (slurp (.getPath tmp))))
      (.delete tmp))))

(deftest package-manager*-test
  (is (script-no-comment=
       (stevedore/checked-script
        "package-manager multiverse"
        (set! tmpfile @("mktemp" -t addscopeXXXX))
        ("cp" -p "/etc/apt/sources.list" @tmpfile)
        ("awk"
         "'{if ($1 ~ /^deb.*/ && ! /multiverse/  ) print $0 \" \" \" multiverse \" ; else print; }'"
         "/etc/apt/sources.list" > @tmpfile)
        ("mv" -f @tmpfile "/etc/apt/sources.list"))
       (second
        (package-manager* action-state :multiverse {:packager :apt}))))
  (with-script-context [:ubuntu :aptitude]
    (is (script-no-comment=
         (stevedore/checked-script
          "package-manager update"
          (chain-or
           ("aptitude" update "-q=2" -y)
           true))
         (second
          (package-manager* action-state :update {:packager :aptitude}))))))

;; (deftest package-manager-update-test
;;   (testing "yum"
;;     (is (script-no-comment=
;;          (first
;;           (build-actions
;;               [session {:target {:group-name :n
;;                                  :node-spec {:image {:os-family :centos}}}}]
;;             (exec-checked-script
;;              session
;;              "package-manager update :enable [\"r1\"]"
;;              ("yum" makecache -q "--enablerepo=r1"))))
;;          (first
;;           (build-actions
;;               [session {:target {:group-name :n
;;                                  :node-spec {:image {:os-family :centos}}}}]
;;             (package-manager session :update {:enable ["r1"]})))))))

;; (deftest package-manager-configure-test
;;   (testing "aptitude"
;;     (is (script-no-comment=
;;          (first
;;           (build-actions [session {}]
;;             (exec-checked-script
;;              session
;;              "package-manager configure :proxy http://192.168.2.37:3182"
;;              ~(->
;;                (remote-file*
;;                 {}
;;                 "/etc/apt/apt.conf.d/50pallet"
;;                 {:content "ACQUIRE::http::proxy \"http://192.168.2.37:3182\";"
;;                  :literal true})
;;                second))))
;;          (first
;;           (build-actions [session {}]
;;             (package-manager
;;              session
;;              :configure {:proxy "http://192.168.2.37:3182"}))))))
;;   (testing "yum"
;;     (is (script-no-comment=
;;          (first
;;           (build-actions
;;               [session {:target {:group-name :n
;;                                  :node-spec {:image {:os-family :centos}}}}]
;;             (exec-checked-script
;;              session
;;              "package-manager configure :proxy http://192.168.2.37:3182"
;;              ~(->
;;                (remote-file*
;;                 {}
;;                 "/etc/yum.pallet.conf"
;;                 {:content "proxy=http://192.168.2.37:3182"
;;                  :literal true})
;;                second)
;;              (if (not @("fgrep" "yum.pallet.conf" "/etc/yum.conf"))
;;                (do
;;                  ("cat" ">>" "/etc/yum.conf" " <<'EOFpallet'")
;;                  "include=file:///etc/yum.pallet.conf"
;;                  "EOFpallet")))))
;;          (first
;;           (build-actions
;;               [session {:target {:group-name :n
;;                                  :node-spec {:image {:os-family :centos}}}}]
;;             (package-manager
;;              session :configure {:proxy "http://192.168.2.37:3182"}))))))
;;   (testing "pacman"
;;     (is (script-no-comment=
;;          (first
;;           (build-actions
;;               [session {:target {:group-name :n
;;                                  :node-sepc {:image {:os-family :arch}}}}]
;;             (exec-checked-script
;;              session
;;              "package-manager configure :proxy http://192.168.2.37:3182"
;;              ~(->
;;                (remote-file*
;;                 {}
;;                 "/etc/pacman.pallet.conf"
;;                 {:content (str "XferCommand = /usr/bin/wget "
;;                                "-e \"http_proxy = http://192.168.2.37:3182\" "
;;                                "-e \"ftp_proxy = http://192.168.2.37:3182\" "
;;                                "--passive-ftp --no-verbose -c -O %o %u")
;;                  :literal true})
;;                second)
;;              (if (not @("fgrep" "pacman.pallet.conf" "/etc/pacman.conf"))
;;                (do
;;                  ~(->
;;                    (sed*
;;                     {}
;;                     "/etc/pacman.conf"
;;                     "a Include = /etc/pacman.pallet.conf"
;;                     :restriction "/\\[options\\]/")
;;                     second))))))
;;          (first
;;           (build-actions
;;               [session {:target {:group-name :n
;;                                  :node-spec {:image {:os-family :arch}}}}]
;;             (package-manager
;;              session :configure {:proxy "http://192.168.2.37:3182"})))))))

;; (deftest add-multiverse-example-test
;;   (testing "apt"
;;     (is (script-no-comment=
;;          (str
;;           (stevedore/checked-script
;;            "package-manager multiverse "
;;            (set! tmpfile @("mktemp" -t addscopeXXXX))
;;            (~lib/cp "/etc/apt/sources.list" @tmpfile :preserve true)
;;            ("awk" "'{if ($1 ~ /^deb.*/ && ! /multiverse/  ) print $0 \" \" \" multiverse \" ; else print; }'" "/etc/apt/sources.list" > @tmpfile)
;;            (~lib/mv @tmpfile "/etc/apt/sources.list" :force true))
;;           (stevedore/checked-script
;;            "package-manager update "
;;            ("apt-get" "-qq" update)))
;;          (first (build-actions [session {}]
;;                   (package-manager session :multiverse)
;;                   (package-manager session :update))))))
;;   (testing "aptitude"
;;     (is (script-no-comment=
;;          (str
;;           (stevedore/checked-script
;;            "package-manager multiverse "
;;            (set! tmpfile @("mktemp" -t addscopeXXXX))
;;            (~lib/cp "/etc/apt/sources.list" @tmpfile :preserve true)
;;            ("awk" "'{if ($1 ~ /^deb.*/ && ! /multiverse/  ) print $0 \" \" \" multiverse \" ; else print; }'" "/etc/apt/sources.list" > @tmpfile)
;;            (~lib/mv @tmpfile "/etc/apt/sources.list" :force true))
;;           (stevedore/checked-script
;;            "package-manager update "
;;            (chain-or
;;             ("aptitude" update "-q=2" -y "")
;;             true)))
;;          (first (build-actions
;;                     [session {:target {:override {:packager :aptitude}
;;                                        :node-spec {:image {:os-family :ubuntu}}}}]
;;                   (package-manager session :multiverse)
;;                   (package-manager session :update)))))))

;; (deftest package-source-test
;;   (let [a (group-spec "a" :packager :aptitude)
;;         b (group-spec "b" :packager :yum :image {:os-family :centos})]
;;     (is (script-no-comment=
;;          (build-script [session {}]
;;            (exec-script*
;;             session
;;             (checked-commands
;;              "Package source"
;;              (->
;;               (remote-file*
;;                {}
;;                "/etc/apt/sources.list.d/source1.list"
;;                {:content "deb http://somewhere/apt $(lsb_release -c -s) main\n"
;;                 :flag-on-changed "packagesourcechanged"})
;;               second))))
;;          (build-script [session {}]
;;            (package-source
;;             session
;;             "source1"
;;             :aptitude {:url "http://somewhere/apt" :scopes ["main"]}
;;             :yum {:url "http://somewhere/yum"}))))
;;     (is
;;      (script-no-comment=

;;       (stevedore/checked-commands
;;        "Package source"
;;        (->
;;         (remote-file*
;;          {}
;;          ;; centos-session
;;          "/etc/yum.repos.d/source1.repo"
;;          {:content
;;           "[source1]\nname=source1\nbaseurl=http://somewhere/yum\ngpgcheck=0\nenabled=1\n"
;;           :literal true
;;           :flag-on-changed "packagesourcechanged"})
;;         second))
;;       (package-source*
;;        ;; centos-session
;;        "source1"
;;        :aptitude {:url "http://somewhere/apt"
;;                   :scopes ["main"]}
;;        :yum {:url "http://somewhere/yum"})))
;;     (testing "ppa pre 12.10"
;;       (is (script-no-comment=
;;            (first
;;             (build-actions [session {}]
;;               (exec-checked-script
;;                session
;;                "Package source"
;;                ("apt-cache" show "python-software-properties" ">" "/dev/null")
;;                (~lib/install-package "python-software-properties")
;;                (when
;;                    (not
;;                     (file-exists?
;;                      "/etc/apt/sources.list.d/abc-$(lsb_release -c -s).list"))
;;                  (chain-and
;;                   (pipe (println) ("add-apt-repository" "ppa:abc"))
;;                   (~lib/update-package-list))))))
;;            (first
;;             (build-actions
;;                 [session {:server {:image
;;                                    {:os-family :ubuntu :os-version "12.04"}}}]
;;               (package-source
;;                session
;;                "source1"
;;                :aptitude {:url "ppa:abc"}
;;                :yum {:url "http://somewhere/yum"}))))))
;;     (testing "ppa for 12.10"
;;       (is (script-no-comment=
;;            (first
;;             (build-actions
;;                 [session {}]
;;               (exec-checked-script
;;                session
;;                "Package source"
;;                ("apt-cache" show "software-properties-common" ">" "/dev/null")
;;                (~lib/install-package "software-properties-common")
;;                (when
;;                    (not
;;                     (file-exists?
;;                      "/etc/apt/sources.list.d/abc-$(lsb_release -c -s).list"))
;;                  (chain-and
;;                   (pipe (println) ("add-apt-repository" "ppa:abc"))
;;                   (~lib/update-package-list))))))
;;            (first
;;             (build-actions
;;                 [session {:server
;;                           {:image {:os-family :ubuntu :os-version "12.10"}}}]
;;               (package-source
;;                session
;;                "source1"
;;                :aptitude {:url "ppa:abc"}
;;                :yum {:url "http://somewhere/yum"}))))))
;;     (is (script-no-comment=
;;          (stevedore/checked-commands
;;           "Package source"
;;           (->
;;            (remote-file*
;;             {}
;;             ;; ubuntu-session
;;             "/etc/apt/sources.list.d/source1.list"
;;             {:content "deb http://somewhere/apt $(lsb_release -c -s) main\n"
;;              :flag-on-changed "packagesourcechanged"})
;;            second)
;;           (stevedore/script
;;            ("apt-key" adv "--keyserver" subkeys.pgp.net "--recv-keys" 1234)))
;;          (package-source*
;;           ;; ubuntu-session
;;           "source1"
;;           :aptitude {:url "http://somewhere/apt"
;;                      :scopes ["main"]
;;                      :key-id 1234}
;;           :yum {:url "http://somewhere/yum"})))
;;     (testing "key-server"
;;       (is (script-no-comment=
;;            (stevedore/checked-commands
;;             "Package source"
;;             (->
;;              (remote-file*
;;               {}
;;               ;; ubuntu-session
;;               "/etc/apt/sources.list.d/source1.list"
;;               {:content "deb http://somewhere/apt $(lsb_release -c -s) main\n"
;;                :flag-on-changed "packagesourcechanged"})
;;              second)
;;             (stevedore/script
;;              ("apt-key" adv "--keyserver" keys.ubuntu.com "--recv-keys" 1234)))
;;            (package-source*
;;             ;; ubuntu-session
;;             "source1"
;;             :aptitude {:url "http://somewhere/apt"
;;                        :scopes ["main"]
;;                        :key-server "keys.ubuntu.com"
;;                        :key-id 1234}
;;             :yum {:url "http://somewhere/yum"}))))))

;; (deftest package-source-test
;;   (let [a (group-spec "a" :override {:packager :aptitude})
;;         b (group-spec "b" :override {:packager :yum
;;                                      :os-family :centos})]
;;     (is (script-no-comment=
;;          (build-script [session {}]
;;            (exec-checked-script
;;             session
;;             "Package source"
;;             ~(->
;;               (remote-file*
;;                {}
;;                ;; (build-session {:server a})
;;                "/etc/apt/sources.list.d/source1.list"
;;                {:content "deb http://somewhere/apt $(lsb_release -c -s) main\n"
;;                 :flag-on-changed "packagesourcechanged"})
;;               second)))
;;          (build-script [session {:target a}]
;;            (package-source
;;             session
;;             "source1"
;;             {:url "http://somewhere/apt"
;;              :scopes ["main"]}))))
;;     (is (script-no-comment=
;;          (build-script [session centos-session]
;;            (exec-checked-script
;;             session
;;             "Package source"
;;             ~(->
;;               (remote-file*
;;                {}
;;                ;; centos-session
;;                "/etc/yum.repos.d/source1.repo"
;;                {:content
;;                 "[source1]\nname=source1\nbaseurl=http://somewhere/yum\ngpgcheck=0\nenabled=1\n"
;;                 :flag-on-changed "packagesourcechanged"
;;                 :literal true})
;;               second)))
;;          (build-script [session centos-session]
;;            (package-source
;;             session
;;             "source1"
;;             {:url "http://somewhere/yum"}))))))

(deftest packages-test
  (is (script-no-comment=
       (with-script-context [:ubuntu :apt]
         (stevedore/checked-script
          "Packages"
          (lib/package-manager-non-interactive)
          (defn enableStart [] (lib/rm "/usr/sbin/policy-rc.d"))
          ("apt-get" -q -y install git+ git2+)))
       (second (packages* {} ["git" "git2"] {:packager :apt}))))
  (with-script-context [:centos]
    (is (script-no-comment=
         (second (package* {} "git-yum" {:packager :yum}))
         (second (packages* {} ["git-yum"] {:packager :yum}))))))

;; (deftest adjust-packages-test
;;   (testing "apt"
;;     (script/with-script-context [:apt]
;;       (is (script-no-comment=
;;            (build-script [session {}]
;;              (exec-script*
;;               session
;;               (stevedore/checked-script
;;                "Packages"
;;                (~lib/package-manager-non-interactive)
;;                (defn enableStart [] (lib/rm "/usr/sbin/policy-rc.d"))
;;                (chain-and
;;                 ("apt-get" -q -y install p1- p4_ p2+ p3+)
;;                 ("dpkg" "--get-selections")))))
;;            (build-script [session {}]
;;              (exec-script*
;;               session
;;               (adjust-packages
;;                :apt
;;                [{:package "p1" :action :remove}
;;                 {:package "p2" :action :install}
;;                 {:package "p3" :action :upgrade}
;;                 {:package "p4" :action :remove :purge true}])))))))
;;   (testing "apt with disabled package start"
;;     (script/with-script-context [:apt]
;;       (is (script-no-comment=
;;            (build-script [session {}]
;;              (exec-script*
;;               session
;;               (stevedore/checked-script
;;                "Packages"
;;                (~lib/package-manager-non-interactive)
;;                (defn enableStart [] (lib/rm "/usr/sbin/policy-rc.d"))
;;                (chain-and
;;                 ("trap" enableStart EXIT)
;;                 ("{" ("cat" > "/usr/sbin/policy-rc.d"
;;                       "<<EOFpallet\n#!/bin/sh\nexit 101\nEOFpallet\n") "}")
;;                 ("apt-get" -q -y install p1- p4_ p2+ p3+)
;;                 ("enableStart")
;;                 ("trap" - EXIT)
;;                 ("dpkg" "--get-selections")))))
;;            (build-script [session {}]
;;              (exec-script*
;;               session
;;               (adjust-packages
;;                :apt
;;                [{:package "p1" :action :remove :disable-service-start true}
;;                 {:package "p2" :action :install :disable-service-start true}
;;                 {:package "p3" :action :upgrade :disable-service-start true}
;;                 {:package "p4" :action :remove :purge true
;;                  :disable-service-start true}])))))))
;;   (testing "aptitude"
;;     (script/with-script-context [:aptitude]
;;       (is (script-no-comment=
;;            (build-script [session {}]
;;              (exec-checked-script
;;               session
;;               "Packages"
;;               (~lib/package-manager-non-interactive)
;;               (defn enableStart [] (lib/rm "/usr/sbin/policy-rc.d"))
;;               ("aptitude" install -q -y p1- p4_ p2+ p3+)
;;               (not (pipe
;;                     ("aptitude" search (quoted "?and(?installed, ?name(^p1$))"))
;;                     ("grep" (quoted p1))))
;;               (pipe
;;                ("aptitude" search (quoted "?and(?installed, ?name(^p2$))"))
;;                ("grep" (quoted p2)))
;;               (pipe
;;                ("aptitude" search (quoted "?and(?installed, ?name(^p3$))"))
;;                ("grep" (quoted p3)))
;;               (not (pipe
;;                     ("aptitude" search (quoted "?and(?installed, ?name(^p4$))"))
;;                     ("grep" (quoted "p4"))))))
;;            (build-script [session (assoc-in
;;                                    ubuntu-session
;;                                    [:target :override :packager] :aptitude)]
;;              (exec-script*
;;               session
;;               (adjust-packages
;;                :aptitude
;;                [{:package "p1" :action :remove}
;;                 {:package "p2" :action :install}
;;                 {:package "p3" :action :upgrade}
;;                 {:package "p4" :action :remove :purge true}])))))))
;;   (testing "aptitude with enable"
;;     (script/with-script-context [:aptitude]
;;       (is (script-no-comment=
;;            (build-script [session {}]
;;              (exec-checked-script
;;               session
;;               "Packages"
;;               (~lib/package-manager-non-interactive)
;;               (defn enableStart [] (lib/rm "/usr/sbin/policy-rc.d"))
;;               ("aptitude" install -q -y -t r1 p2+)
;;               ("aptitude" install -q -y p1+)
;;               (pipe
;;                ("aptitude" search (quoted "?and(?installed, ?name(^p1$))"))
;;                ("grep" (quoted p1)))
;;               (pipe
;;                ("aptitude" search (quoted "?and(?installed, ?name(^p2$))"))
;;                ("grep" (quoted "p2")))))
;;            (build-script [session (assoc-in ubuntu-session
;;                                             [:target :override :packager] :aptitude)]
;;              (exec-script*
;;               session
;;               (adjust-packages
;;                :aptitude
;;                [{:package "p1" :action :install :priority 20}
;;                 {:package "p2" :action :install :enable ["r1"]
;;                  :priority 2}])))))))
;;   (testing "aptitude with allow-unsigned"
;;     (script/with-script-context [:aptitude]
;;       (is (script-no-comment=
;;            (build-script [session {}]
;;              (exec-checked-script
;;               session
;;               "Packages"
;;               (~lib/package-manager-non-interactive)
;;               (defn enableStart [] (lib/rm "/usr/sbin/policy-rc.d"))
;;               ("aptitude" install -q -y p1+)
;;               ("aptitude" install -q -y
;;                -o "'APT::Get::AllowUnauthenticated=true'" p2+)
;;               (pipe
;;                ("aptitude" search (quoted "?and(?installed, ?name(^p1$))"))
;;                ("grep" (quoted p1)))
;;               (pipe
;;                ("aptitude" search (quoted "?and(?installed, ?name(^p2$))"))
;;                ("grep" (quoted "p2")))))
;;            (build-script [session (assoc-in ubuntu-session
;;                                             [:target :override :packager] :aptitude)]
;;              (exec-script*
;;               session
;;               (adjust-packages
;;                :aptitude
;;                [{:package "p1" :action :install}
;;                 {:package "p2" :action :install :allow-unsigned true}])))))))
;;   (testing "yum"
;;     (is (script-no-comment=
;;          (build-script [session {}]
;;            (exec-checked-script
;;             session
;;             "Packages"
;;             ("yum" install -q -y p2)
;;             ("yum" remove -q -y p1 p4)
;;             ("yum" upgrade -q -y p3)
;;             ("yum" list installed)))
;;          (build-script [session centos-session]
;;            (exec-script*
;;             session
;;             (adjust-packages
;;              :yum
;;              [{:package "p1" :action :remove}
;;               {:package "p2" :action :install}
;;               {:package "p3" :action :upgrade}
;;               {:package "p4" :action :remove :purge true}]))))))
;;   (testing "yum with disable and priority"
;;     (is (script-no-comment=
;;          (build-script [session {}]
;;            (stevedore/checked-script
;;             session
;;             "Packages"
;;             ("yum" install -q -y "--disablerepo=r1" p2)
;;             ("yum" install -q -y p1)
;;             ("yum" list installed)))
;;          (build-script [session centos-session]
;;            (adjust-packages
;;             :yum
;;             [{:package "p1" :action :install :priority 50}
;;              {:package "p2" :action :install :disable ["r1"]
;;               :priority 25}]))))
;;     (is (script-no-comment=
;;          (first
;;           (build-actions [session centos-session]
;;             (exec-checked-script
;;              session
;;              "Packages"
;;              ("yum" install -q -y p1)
;;              ("yum" list installed))
;;             (exec-checked-script
;;              session
;;              "Packages"
;;              ("yum" install -q -y "--disablerepo=r1" p2)
;;              ("yum" list installed))))
;;          (first
;;           (build-actions [session centos-session]
;;             (package "p1")
;;             (package "p2" {:disable ["r1"] :priority 25})))))))

;; (deftest add-rpm-test
;;   (is (script-no-comment=
;;        (build-script [session centos-session]
;;          (remote-file session "jpackage-utils-compat" :url "http:url")
;;          (exec-checked-script
;;           session
;;           "Install rpm jpackage-utils-compat"
;;           (if-not ("rpm" -q @("rpm" -pq "jpackage-utils-compat")
;;                    > "/dev/null" "2>&1")
;;             (do ("rpm" -U --quiet "jpackage-utils-compat")))))
;;        (build-script [session centos-session]
;;          (add-rpm session "jpackage-utils-compat" :url "http:url")))))

;; (deftest debconf-set-selections-test
;;   (is (script-no-comment=
;;        (first
;;         (build-actions [session {}]
;;           (exec-checked-script
;;            session
;;            "Preseed a b c d"
;;            (pipe (println (quoted "a b c d"))
;;                  ("/usr/bin/debconf-set-selections")))))
;;        (first
;;         (build-actions [session {}]
;;           (debconf-set-selections session {:line "a b c d"})))))
;;   (is (script-no-comment=
;;        (first
;;         (build-actions [session {}]
;;           (exec-checked-script
;;            session
;;            "Preseed p q :select true"
;;            (pipe (println (quoted "p q select true"))
;;                  ("/usr/bin/debconf-set-selections")))))
;;        (first
;;         (build-actions [session {}]
;;           (debconf-set-selections
;;            session
;;            {:package "p"
;;             :question "q"
;;             :type :select
;;             :value true}))))))
