package fr.baptiste.bank.domain.model

import fr.baptiste.bank.domain.money.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EnvelopeTreeTest {
    private fun node(id: Long, parent: Long?, euros: Long = 0, position: Int = 0) =
        TreeNode(id, parent, position, Money.euros(euros))

    @Test
    fun aParentCoversItsChildrenWithoutBeingAddedToThem() {
        // A à 300 € contient B à 200 € qui contient C à 400 €.
        val tree = EnvelopeTree(listOf(node(1, null, 300), node(2, 1, 200), node(3, 2, 400)))
        assertEquals(Money.euros(400), tree.covered(2))
        assertEquals(Money.euros(400), tree.covered(1))
        assertEquals(Money.euros(400), tree.total)
    }

    @Test
    fun aParentComesBackToItsAmountWhenAChildGoesDown() {
        val tree = EnvelopeTree(listOf(node(1, null, 300), node(2, 1, 200), node(3, 2, 100)))
        assertEquals(Money.euros(200), tree.covered(2))
        assertEquals(Money.euros(300), tree.covered(1))
    }

    @Test
    fun ownPartsAddUpToThePlan() {
        val nodes = listOf(node(1, null, 500), node(2, 1, 200), node(3, 1, 100), node(4, null, 50))
        val tree = EnvelopeTree(nodes)
        assertEquals(Money.euros(200), tree.ownPart(1))
        assertEquals(Money.euros(550), nodes.fold(Money.ZERO) { sum, it -> sum + tree.ownPart(it.id) })
        assertEquals(tree.total, nodes.fold(Money.ZERO) { sum, it -> sum + tree.ownPart(it.id) })
    }

    @Test
    fun aGroupWithoutAmountIsTheSumOfItsChildren() {
        val tree = EnvelopeTree(listOf(node(1, null), node(2, 1, 120), node(3, 1, 80)))
        assertEquals(Money.euros(200), tree.covered(1))
        assertEquals(Money.ZERO, tree.ownPart(1))
    }

    @Test
    fun aBranchAddsUpEveryLevel() {
        // Transport : Voiture (Essence) et Bus, à trois niveaux différents.
        val tree = EnvelopeTree(listOf(node(1, null), node(2, 1), node(3, 2), node(4, 1)))
        val spent = mapOf(2L to Money.euros(40), 3L to Money.euros(60), 4L to Money.euros(25))
        assertEquals(Money.euros(125), tree.branchTotal(1, spent))
        assertEquals(Money.euros(100), tree.branchTotal(2, spent))
    }

    @Test
    fun anEnvelopeNeverGoesIntoItselfOrWhatItContains() {
        val tree = EnvelopeTree(listOf(node(1, null), node(2, 1), node(3, 2)))
        assertFalse(tree.canMoveInto(1, 1))
        assertFalse(tree.canMoveInto(1, 3))
        assertTrue(tree.canMoveInto(3, 1))
        assertTrue(tree.canMoveInto(3, null))
        assertNull(tree.move(1, TreeDrop.Into(3)))
    }

    @Test
    fun dropsPlaceTheEnvelopeWhereItIsAsked() {
        val tree = EnvelopeTree(
            listOf(node(1, null, position = 0), node(2, null, position = 1), node(3, null, position = 2), node(4, 1)),
        )
        val into = tree.move(3, TreeDrop.Into(1))!!.single { it.id == 3L }
        assertEquals(1L, into.parentId)
        assertEquals(1, into.position)
        val before = tree.move(3, TreeDrop.Before(2))!!.associateBy { it.id }
        assertEquals(1, before[3]?.position)
        assertEquals(2, before[2]?.position)
        val after = tree.move(1, TreeDrop.After(3))!!.associateBy { it.id }
        assertEquals(2, after[1]?.position)
        assertEquals(0, after[2]?.position)
        // Sortir d'un parent vers le haut.
        val top = tree.move(4, TreeDrop.Into(null))!!.single()
        assertEquals(null, top.parentId)
        assertEquals(3, top.position)
    }

    @Test
    fun droppingOnItselfIsRefused() {
        val tree = EnvelopeTree(listOf(node(1, null), node(2, null, position = 1)))
        assertNull(tree.move(1, TreeDrop.Before(1)))
        assertNull(tree.move(1, TreeDrop.After(1)))
    }

    @Test
    fun aLoopInTheDataEndsInsteadOfBlocking() {
        val tree = EnvelopeTree(listOf(node(1, 2, 10), node(2, 1, 20)))
        assertEquals(Money.euros(20), tree.covered(1))
        assertEquals(1, tree.descendants(1).size)
    }

    @Test
    fun anUnknownParentPutsTheEnvelopeAtTheTop() {
        val tree = EnvelopeTree(listOf(node(1, 99, 10)))
        assertEquals(listOf(1L), tree.children(null).map { it.id })
    }
}
