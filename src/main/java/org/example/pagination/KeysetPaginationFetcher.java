package org.example.pagination;

import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.function.BiFunction;
import java.util.function.Supplier;

@Component
public class KeysetPaginationFetcher {

    public <E extends KeysetEntity> Slice<E> fetchSlice(
            PaginationMode mode,
            Supplier<Slice<E>> firstPageSupplier,
            BiFunction<LocalDateTime, Long, Slice<E>> nextPageSupplier,
            LocalDateTime cursorCreatedAt,
            Long cursorId) {

        if (mode == PaginationMode.FIRST) {
            return firstPageSupplier.get();
        }
        return nextPageSupplier.apply(cursorCreatedAt, cursorId);

    }

}
