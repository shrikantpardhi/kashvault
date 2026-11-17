package com.kashvault.util

import com.kashvault.domain.Person
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PersonSearchTest {
    private val people = listOf(
        Person("p1", "Asha", "999"),
        Person("p2", "Rahul", "888"),
        Person("p3", "Amit", null),
    )

    @Test
    fun matchesNameAndHidesUnrelated() {
        assertEquals(listOf("Asha"), matchingPeople(people, "ash").map { it.name })
        assertEquals(listOf("Rahul"), matchingPeople(people, "rah").map { it.name })
        assertEquals(listOf("Amit"), matchingPeople(people, "amit").map { it.name })
        assertTrue(matchingPeople(people, "   ").isEmpty())
    }

    @Test
    fun offersCreateWhenNoExactPerson() {
        assertTrue(canCreatePerson(people, "Neha"))
        assertFalse(canCreatePerson(people, "Asha"))
        assertFalse(canCreatePerson(people, "  "))
    }
}
