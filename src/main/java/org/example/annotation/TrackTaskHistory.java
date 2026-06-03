package org.example.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD) // Аннотацию можно вешать только на методы
@Retention(RetentionPolicy.RUNTIME) // Аннотация будет доступна во время работы программы (чтобы AOP ее увидел)
public @interface TrackTaskHistory {
}