(ns frontend.common.file.util-test
  (:require [cljs.test :refer [deftest is testing]]
            [frontend.common.file.util :as wfu]))

(deftest file-name-sanity-test
  (testing "Normal titles"
    (is (= "Hello World" (wfu/file-name-sanity "Hello World")))
    (is (= "My Page" (wfu/file-name-sanity "My Page"))))

  (testing "Reserved characters"
    (is (= "a%3Ab" (wfu/file-name-sanity "a:b")))
    (is (= "a%2Ab" (wfu/file-name-sanity "a*b")))
    (is (= "a%3Fb" (wfu/file-name-sanity "a?b")))
    (is (= "a%22b" (wfu/file-name-sanity "a\"b")))
    (is (= "a%3Cb" (wfu/file-name-sanity "a<b")))
    (is (= "a%3Eb" (wfu/file-name-sanity "a>b")))
    (is (= "a%7Cb" (wfu/file-name-sanity "a|b")))
    (is (= "a%23b" (wfu/file-name-sanity "a#b")))
    (is (= "a%5Cb" (wfu/file-name-sanity "a\\b"))))

  (testing "Slashes"
    (is (= "a___b" (wfu/file-name-sanity "a/b")))
    (is (= "a___b___c" (wfu/file-name-sanity "a/b/c")))
    (is (= "a___b" (wfu/file-name-sanity "/a/b/"))))

  (testing "Windows reserved filebodies"
    (is (= "CON___" (wfu/file-name-sanity "CON")))
    (is (= "PRN___" (wfu/file-name-sanity "PRN")))
    (is (= "AUX___" (wfu/file-name-sanity "AUX")))
    (is (= "NUL___" (wfu/file-name-sanity "NUL")))
    (is (= "COM1___" (wfu/file-name-sanity "COM1")))
    (is (= "LPT1___" (wfu/file-name-sanity "LPT1"))))

  (testing "Titles ending with dot"
    (is (= "test.___" (wfu/file-name-sanity "test."))))

  (testing "Existing URL encoding"
    (is (= "foo%2520bar" (wfu/file-name-sanity "foo%20bar"))))

  (testing "Triple lowbars and ambiguous lowbars"
    (is (= "a%5F%5F%5Fb" (wfu/file-name-sanity "a___b")))
    (is (= "a%5F___b" (wfu/file-name-sanity "a_/b")))
    (is (= "a___%5Fb" (wfu/file-name-sanity "a/_b")))))

(deftest include-reserved-chars?-test
  (testing "Strings without reserved characters"
    (is (not (wfu/include-reserved-chars? "Hello World")))
    (is (not (wfu/include-reserved-chars? "My_Page"))))

  (testing "Strings with reserved characters"
    (is (wfu/include-reserved-chars? "a:b"))
    (is (wfu/include-reserved-chars? "a*b"))
    (is (wfu/include-reserved-chars? "a?b"))
    (is (wfu/include-reserved-chars? "a\"b"))
    (is (wfu/include-reserved-chars? "a<b"))
    (is (wfu/include-reserved-chars? "a>b"))
    (is (wfu/include-reserved-chars? "a|b"))
    (is (wfu/include-reserved-chars? "a#b"))
    (is (wfu/include-reserved-chars? "a\\b"))))
