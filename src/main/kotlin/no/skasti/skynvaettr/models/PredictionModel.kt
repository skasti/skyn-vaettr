package no.skasti.skynvaettr.models

import no.skasti.skynvaettr.expectations.Prediction

/** A model that turns an arbitrary model input into a signal prediction. */
fun interface PredictionModel<in I, T> {
    fun predict(input: I): Prediction<T>
}

/**
 * A prediction model that can learn from resolved experience.
 *
 * The target has deliberately no forecast horizon. Training policy decides which observed outcome
 * should teach the model and therefore what temporal semantics the model learns.
 */
interface TrainablePredictionModel<in I, T> : PredictionModel<I, T> {
    fun train(
        input: I,
        target: T,
        weight: Double = 1.0,
    )
}
