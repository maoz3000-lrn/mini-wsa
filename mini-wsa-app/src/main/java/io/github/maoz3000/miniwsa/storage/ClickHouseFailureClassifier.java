package io.github.maoz3000.miniwsa.storage;

import com.clickhouse.client.api.ServerException;
import io.github.maoz3000.miniwsa.domain.error.PermanentStorageException;
import io.github.maoz3000.miniwsa.domain.error.StorageException;
import io.github.maoz3000.miniwsa.domain.error.TransientStorageException;

import java.sql.SQLException;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Decides whether a ClickHouse failure is about the data (permanent: retrying cannot help) or about the
 * environment (transient). Only known bad-data error codes are permanent; anything unrecognised is treated as
 * transient, so an outage can never push good events to the dead-letter topic.
 */
final class ClickHouseFailureClassifier {

    /**
     * ClickHouse error codes meaning "this data cannot be stored", see ErrorCodes.cpp.
     */
    static final Set<Integer> BAD_DATA_CODES = Set.of(
            6,   // CANNOT_PARSE_TEXT
            27,  // CANNOT_PARSE_INPUT_ASSERTION_FAILED
            38,  // CANNOT_PARSE_DATE
            41,  // CANNOT_PARSE_DATETIME
            53,  // TYPE_MISMATCH
            69,  // ARGUMENT_OUT_OF_BOUND
            70,  // CANNOT_CONVERT_TYPE
            72,  // CANNOT_PARSE_NUMBER
            117, // INCORRECT_DATA
            321, // VALUE_IS_OUT_OF_RANGE_OF_DATA_TYPE
            349, // CANNOT_INSERT_NULL_IN_ORDINARY_COLUMN
            691  // UNKNOWN_ELEMENT_OF_ENUM
    );

    private ClickHouseFailureClassifier() {
    }

    static StorageException classify(String message, Throwable failure) {
        return errorCode(failure).filter(BAD_DATA_CODES::contains).isPresent()
                ? new PermanentStorageException(message, failure)
                : new TransientStorageException(message, failure);
    }

    static Optional<Integer> errorCode(Throwable failure) {
        return Stream.iterate(failure, t -> t != null, Throwable::getCause)
                .map(ClickHouseFailureClassifier::codeOf)
                .flatMap(Optional::stream)
                .findFirst();
    }

    private static Optional<Integer> codeOf(Throwable t) {
        return switch (t) {
            case ServerException server -> Optional.of(server.getCode());
            case SQLException sql when sql.getErrorCode() > 0 -> Optional.of(sql.getErrorCode());
            default -> Optional.empty();
        };
    }
}
