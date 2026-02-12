package org.example.pagination;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Function;

@Component
public class KeysetPageBuilder {

    public <E extends KeysetEntity, D> KeysetPageResponseDto<D> universalBuilder(
            KeysetSliceResult<E> sliceResult,
            Function<E, D> mapper,
            int pageSize
    ) {

        List<E> entities = sliceResult.getItems();

        List<D> dtoItems = entities.stream()
                .map(mapper).toList();

        return KeysetPageResponseDto.<D>builder()
                .items(dtoItems)
                .limit(pageSize)
                .cursorCreatedAt(sliceResult.getNextCursorCreatedAt())
                .cursorId(sliceResult.getNextCursorId())
                .hasNext(sliceResult.isHasNext())
                .build();

    }
}
