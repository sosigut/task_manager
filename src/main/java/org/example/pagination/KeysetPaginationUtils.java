package org.example.pagination;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class KeysetPaginationUtils {

    private void validateKeysetCursor(boolean isFirst, boolean isNext) throws IllegalArgumentException {
        if (isFirst || isNext) return;
        throw new IllegalArgumentException("Неверные параметры курсора");
    }

    public PaginationMode cursorMode(LocalDateTime cursorCreatedAt, Long cursorId){

        boolean isFirst = cursorCreatedAt == null && cursorId == null;
        boolean isNext  = cursorCreatedAt != null && cursorId != null;

        validateKeysetCursor(isFirst, isNext);

        if (isFirst){
            return PaginationMode.FIRST;
        } else {
            return PaginationMode.NEXT;
        }
    }

    public int normalizeLimit(Integer limit){
        return limit != null && limit > 0 ? Math.min(limit, 50) : 10;
    }

    public Pageable createPageable(Integer pageSize){

        int querySize = pageSize + 1;

        return PageRequest.of(
                0,
                querySize,
                Sort.by(
                        Sort.Order.desc("createdAt"),
                        Sort.Order.desc("id")
                )
        );

    }

    public <T extends KeysetEntity> KeysetSliceResult<T> trim(Slice<T> slice, int pageSize){

        var content = slice.getContent();

        boolean hasNext = content.size() > pageSize;
        var itemsToReturn = hasNext ? content.subList(0, pageSize) : content;

        LocalDateTime nextCursorCreatedAt = null;
        Long nextCursorId = null;

        if (hasNext && !itemsToReturn.isEmpty()) {
            T last = itemsToReturn.get(itemsToReturn.size() - 1);
            nextCursorCreatedAt = last.getCreatedAt();
            nextCursorId = last.getId();
        }

        return new KeysetSliceResult<>(itemsToReturn, hasNext, nextCursorCreatedAt, nextCursorId);
    }

}
