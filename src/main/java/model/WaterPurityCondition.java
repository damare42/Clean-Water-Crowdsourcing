package model;

/**
 * The overall condition of the water in a {@link WaterPurityReport}.
 */
public enum WaterPurityCondition {
    /** Safe to drink. */
    Safe,
    /** Drinkable after treatment. */
    Treatable,
    /** Not safe to drink. */
    Unsafe
}
