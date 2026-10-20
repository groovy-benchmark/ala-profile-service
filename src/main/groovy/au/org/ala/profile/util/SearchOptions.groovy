package au.org.ala.profile.util

import groovy.transform.ToString

@ToString
class SearchOptions {
    boolean nameOnly = false
    boolean matchAll = false
    boolean includeArchived = false
    boolean searchAla = true
    boolean searchNsl = true
    boolean includeNameAttributes = true
    boolean hideStubs = true
}
