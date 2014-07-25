package com.netflix.serialization;

import static com.google.common.base.Preconditions.checkArgument;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

import com.google.common.reflect.TypeToken;

public abstract class TypeDef<T> {

    // private final Type runtimeType;
    private TypeToken<?> delegate;
    
    @SuppressWarnings("unchecked")
    protected TypeDef() {
        Type superclass = getClass().getGenericSuperclass();
        checkArgument(superclass instanceof ParameterizedType,
            "%s isn't parameterized", superclass);
        Type runtimeType = ((ParameterizedType) superclass).getActualTypeArguments()[0];
        this.delegate = (TypeToken<T>) TypeToken.of(runtimeType);
    }
    
    
    public static <T> TypeDef<T> fromClass(Class<T> classType) {
        TypeDef<T> spec = new TypeDef<T>() {
        };
        spec.delegate = TypeToken.of(classType);
        return spec;
    }
    
    public static TypeDef<?> fromType(Type type) {
        TypeDef<Object> spec = new TypeDef<Object>() {
        };
        spec.delegate = TypeToken.of(type);
        return spec;
    }
    
    public Class<? super T> getRawType() {
        return (Class<? super T>) delegate.getRawType();
    }
    
    public Type getType() {
        return delegate.getType();
    }
    
}
