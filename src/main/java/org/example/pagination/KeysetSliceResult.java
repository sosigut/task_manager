package org.example.pagination;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class KeysetSliceResult<T> {

    List<T> items;

    boolean hasNext;

    LocalDateTime nextCursorCreatedAt;

    Long nextCursorId;

}
