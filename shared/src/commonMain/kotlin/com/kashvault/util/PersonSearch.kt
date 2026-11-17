package com.kashvault.util

import com.kashvault.domain.Person

fun matchingPeople(people: List<Person>, query: String): List<Person> {
    val needle = query.trim()
    if (needle.isEmpty()) return emptyList()
    return people
        .filter { person ->
            person.name.contains(needle, ignoreCase = true) ||
                person.phone.orEmpty().contains(needle)
        }
        .sortedBy { it.name.lowercase() }
        .take(8)
}

fun canCreatePerson(people: List<Person>, query: String): Boolean {
    val needle = query.trim()
    if (needle.isEmpty()) return false
    return people.none { it.name.equals(needle, ignoreCase = true) }
}
