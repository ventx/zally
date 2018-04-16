package de.zalando.zally.util.ast;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;

import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static de.zalando.zally.util.ast.Util.getterNameToPointer;
import static de.zalando.zally.util.ast.Util.rfc6901Encode;

public class ReverseAstBuilder<T> {
    private static Collection<String> EXTENSION_METHOD_NAMES = new HashSet<>(Arrays.asList(
            "getVendorExtensions",
            "getExtensions"
    ));

    public static class ReverseAstException extends Exception {
        ReverseAstException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private final Deque<Node> nodes = new LinkedList<>(); // stack of tree nodes
    private final Map<Object, Node> map = new IdentityHashMap<>(); // map of node objects to JSON pointers
    private final Set<Class<?>> ignore = new HashSet<>(Util.PRIMITIVES);

    ReverseAstBuilder(T root) {
        nodes.push(new Node(root, "#", null));
    }

    /**
     * Add classes that should not be traversed by the ReverseAstBuilder.
     *
     * @param ignore Set of classes in the root object that should be ignored.
     * @return This ReverseAstBuilder.
     */
    public ReverseAstBuilder ignore(Collection<Class<?>> ignore) {
        this.ignore.addAll(ignore);
        return this;
    }

    /**
     * Construct a new ReverseAst instance from the root object in this builder.
     * Traverses a Swagger or OpenApi object tree and constructs a map of object nodes to meta information objects.
     *
     * @return A new ReverseAst instance.
     * @throws ReverseAstException If an error occurs during reflection.
     */
    public ReverseAst<T> build() throws ReverseAstException {
        while (!nodes.isEmpty()) {
            Node node = nodes.pop();

            if (!ignore.contains(node.object.getClass())) {
                Collection<Node> children;
                if (node.object instanceof Map) {
                    children = handleMap((Map<?, ?>) node.object, node.pointer, node.marker);
                } else if (node.object instanceof List) {
                    children = handleList((List<?>) node.object, node.pointer, node.marker);
                } else {
                    children = handleObject(node.object, node.pointer, node.marker);
                }
                for (Node child : children) {
                    nodes.push(child.setParent(node));
                }
                node.setChildren(children);
            }
            if (!node.skip) {
                map.put(node.object, node);
            }
        }
        return new ReverseAst<>(map);
    }

    static Deque<Node> handleMap(Map<?, ?> map, String pointer, Marker marker) {
        Deque<Node> nodes = new LinkedList<>();

        for (Map.Entry<?, ?> entry : map.entrySet()) {
            Object key = entry.getKey();
            Object value = entry.getValue();
            if (key instanceof String && value != null) {
                String newPointer = pointer.concat("/").concat(rfc6901Encode((String) key));
                nodes.push(new Node(value, newPointer, marker));
            }
        }
        return nodes;
    }

    static Deque<Node> handleList(List<?> list, String pointer, Marker marker) {
        Deque<Node> nodes = new LinkedList<>();

        for (int i = 0; i < list.size(); i++) {
            Object value = list.get(i);
            if (value != null) {
                String newPointer = pointer.concat("/").concat(String.valueOf(i));
                nodes.push(new Node(value, newPointer, marker));
            }
        }
        return nodes;
    }

    static Deque<Node> handleObject(Object object, String pointer, Marker marker) throws ReverseAstException {
        Deque<Node> nodes = new LinkedList<>();
        String ignoreExtension = getVendorExtension(object, Marker.TYPE_X_ZALLY_IGNORE);

        if (ignoreExtension != null) {
            marker = new Marker(Marker.TYPE_X_ZALLY_IGNORE, ignoreExtension);
        }
        for (Method m : object.getClass().getDeclaredMethods()) {
            String name = m.getName();
            // Find all public getter methods.
            if (isPublicGetterMethod(m)) {
                try {
                    Object value = m.invoke(object);
                    if (value != null) {
                        if (m.isAnnotationPresent(JsonAnyGetter.class)) {
                            // A `JsonAnyGetter` method is simply a wrapper for nested properties.
                            // We must not use the method name but re-use the current pointer.
                            nodes.push(new Node(value, pointer, marker, /* skip */true));
                        } else {
                            String newPointer = pointer.concat("/").concat(getterNameToPointer(name));
                            nodes.push(new Node(value, newPointer, marker));
                        }
                    }
                } catch (ReflectiveOperationException e) {
                    String message = String.format("Error invoking %s on %s at path %s", name, object.getClass(), pointer);
                    throw new ReverseAstException(message, e);
                }
            }
        }
        return nodes;
    }

    static boolean isPublicGetterMethod(Method m) {
        return m.getName().startsWith("get")
                && m.getParameterCount() == 0
                && Modifier.isPublic(m.getModifiers())
                && !m.isAnnotationPresent(JsonIgnore.class);
    }

    @Nullable
    static String getVendorExtension(Object object, String extension) throws ReverseAstException {
        for (Method m : object.getClass().getDeclaredMethods()) {
            if (EXTENSION_METHOD_NAMES.contains(m.getName())) {
                try {
                    Object extensions = m.invoke(object);
                    if (extensions instanceof Map) {
                        return (String) ((Map) extensions).get(extension);
                    }
                } catch (ReflectiveOperationException e) {
                    throw new ReverseAstException("Error getting extensions.", e);
                }
            }
        }
        return null;
    }
}
