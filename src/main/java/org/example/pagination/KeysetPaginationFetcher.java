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
            Long cursorId) throws IllegalArgumentException {

        if (mode == PaginationMode.NEXT) {
            if (cursorCreatedAt == null || cursorId == null) {
                throw new IllegalArgumentException(
                        "Для следующей страницы необходимо указать cursorCreatedAt и cursorId"
                );
            }
            return nextPageSupplier.apply(cursorCreatedAt, cursorId);
        }

        if (mode == PaginationMode.FIRST) {
            if (cursorCreatedAt != null || cursorId != null) {
                throw new IllegalArgumentException(
                        "Для первой страницы не должны передаваться cursorCreatedAt и cursorId"
                );
            }
            return firstPageSupplier.get();
        }

        throw new IllegalArgumentException("Неизвестный режим пагинации: " + mode);

    }

}
