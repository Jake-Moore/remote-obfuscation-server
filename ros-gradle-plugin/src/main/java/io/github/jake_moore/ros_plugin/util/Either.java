package io.github.jake_moore.ros_plugin.util;

import java.util.Optional;

/**
 * An object that represents either an (L) type or an (R) type. Never both values, never neither values.
 * @param <L> the left type
 * @param <R> the right type
 */
public abstract class Either<L, R> {
    private Either() {}

    /**
     * Gets the left value of this either instance.
     *
     * @return the left value
     */
    public abstract Optional<L> left();

    /**
     * Gets the right value of this instance.
     *
     * @return the right value.
     */
    public abstract Optional<R> right();

    public static <L, R> Either<L, R> left(L left) {
        return new Left<>(left);
    }

    public static <L, R> Either<L, R> right(R right) {
        return new Right<>(right);
    }

    /**
     * Gets whichever value is present
     *
     * @return the value.
     */
    public abstract Object get();

    /**
     * Represents the left value of an {@link Either} instance.
     * @param <L> the left type
     * @param <R> the right type
     */
    public static class Left<L, R> extends Either<L, R> {
        private final L left;
        public Left(L left) {
            this.left = left;
        }

        @Override
        public Optional<L> left() {
            return Optional.of(left);
        }

        @Override
        public Optional<R> right() {
            return Optional.empty();
        }

        @Override
        public Object get() {
            return left;
        }
    }

    /**
     * Represents the right value of an {@link Either} instance.
     * @param <L> the left type
     * @param <R> the right type
     */
    public static class Right<L, R> extends Either<L, R> {
        private final R right;
        public Right(R right) {
            this.right = right;
        }


        @Override
        public Optional<L> left() {
            return Optional.empty();
        }

        @Override
        public Optional<R> right() {
            return Optional.of(right);
        }

        @Override
        public Object get() {
            return right;
        }
    }
}