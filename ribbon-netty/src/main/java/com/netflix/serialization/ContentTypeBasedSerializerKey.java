package com.netflix.serialization;

public class ContentTypeBasedSerializerKey {
    private final String contentType;
    private final Class<?> classType;
    
    public ContentTypeBasedSerializerKey(String contentType, Class<?> classType) {
        super();
        this.contentType = contentType;
        this.classType = classType;
    }

    public final String getContentType() {
        return contentType;
    }

    public final Class<?> getClassType() {
        return classType;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result
                + ((classType == null) ? 0 : classType.hashCode());
        result = prime * result
                + ((contentType == null) ? 0 : contentType.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        ContentTypeBasedSerializerKey other = (ContentTypeBasedSerializerKey) obj;
        if (classType == null) {
            if (other.classType != null)
                return false;
        } else if (!classType.equals(other.classType))
            return false;
        if (contentType == null) {
            if (other.contentType != null)
                return false;
        } else if (!contentType.equals(other.contentType))
            return false;
        return true;
    }

    @Override
    public String toString() {
        return "DefaultSerializerKey [contentType=" + contentType
                + ", classType=" + classType + "]";
    }
    
    
    
}
