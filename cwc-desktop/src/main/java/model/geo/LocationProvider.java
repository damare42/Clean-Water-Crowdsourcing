package model.geo;

import java.util.Optional;

/**
 * Finds the approximate location of this computer from outside (an IP lookup).
 */
@FunctionalInterface
public interface LocationProvider {

    /**
     * Looks the location up. Blocks for at most a few seconds; never throws for
     * network or format problems.
     *
     * @return the location, or empty if it could not be found
     */
    Optional<ApproximateLocation> lookup();
}
