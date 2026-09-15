package no.skasti.skynvaettr.models

import kotlin.test.Test
import kotlin.test.assertEquals
import no.skasti.skynvaettr.representation.Embedding
import no.skasti.skynvaettr.representation.Representation

class OnlineKnnModelTest {
    @Test
    fun `distinguishes sequences that have the same pooled mean`() {
        val model = OnlineKnnModel(neighbours = 1)
        val ascending = Representation.of(
            Embedding.of(0.0, 0.0),
            Embedding.of(1.0, 1.0),
        )
        val descending = Representation.of(
            Embedding.of(1.0, 1.0),
            Embedding.of(0.0, 0.0),
        )

        model.train(ascending, Representation.of(Embedding.of(2.0)))
        model.train(descending, Representation.of(Embedding.of(8.0)))

        assertEquals(2.0, model.forward(ascending)[0][0], absoluteTolerance = 1e-9)
        assertEquals(8.0, model.forward(descending)[0][0], absoluteTolerance = 1e-9)
    }

    @Test
    fun `supports histories with different position counts`() {
        val model = OnlineKnnModel(neighbours = 1)
        val shortHistory = Representation.of(
            Embedding.of(0.0, -1.0),
            Embedding.of(1.0, 0.0),
        )
        val longHistory = Representation.of(
            Embedding.of(0.0, -2.0),
            Embedding.of(0.0, -1.0),
            Embedding.of(1.0, 0.0),
        )

        model.train(shortHistory, Representation.of(Embedding.of(4.0)))

        val prediction = model.forward(longHistory)
        assertEquals(4.0, prediction[0][0], absoluteTolerance = 1e-9)
    }
}
