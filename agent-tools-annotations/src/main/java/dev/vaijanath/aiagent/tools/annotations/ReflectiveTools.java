package dev.vaijanath.aiagent.tools.annotations;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.vaijanath.aiagent.tool.Tool;
import dev.vaijanath.aiagent.tool.ToolResult;
import dev.vaijanath.aiagent.tool.ToolSpec;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Turns {@link AgentTool}-annotated methods on an object into {@link Tool}s, deriving each tool's
 * JSON-schema parameters from the method signature — so tools are ordinary typed Java methods instead
 * of hand-written schema strings plus manual argument parsing.
 *
 * <p>Incoming JSON arguments are bound to the method parameters by name (Jackson); the return value
 * becomes the result — a {@code String} as-is, a {@link ToolResult} passed through, anything else
 * serialized to JSON, {@code void}/{@code null} as empty. Schema generation covers strings, numbers,
 * booleans, enums, arrays/collections, and records (recursively); unknown types fall back to a
 * permissive object schema. A parameter is required unless {@link ToolParam#required()} is false.
 *
 * <p>Failures are contained: malformed arguments yield a safe {@code error} result the model can
 * retry, and an exception thrown by the tool method never leaks its detail into the model's context.
 */
public final class ReflectiveTools {

    private static final Logger log = LoggerFactory.getLogger(ReflectiveTools.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final Set<Class<?>> STRING_TYPES = Set.of(String.class, char.class, Character.class);
    private static final Set<Class<?>> INTEGER_TYPES = Set.of(
            int.class, Integer.class, long.class, Long.class,
            short.class, Short.class, byte.class, Byte.class, BigInteger.class);
    private static final Set<Class<?>> NUMBER_TYPES = Set.of(
            double.class, Double.class, float.class, Float.class, BigDecimal.class);

    private ReflectiveTools() {}

    /** A {@link Tool} for every public {@link AgentTool}-annotated method on {@code target}. */
    public static List<Tool> from(Object target) {
        Objects.requireNonNull(target, "target");
        List<Tool> tools = new ArrayList<>();
        for (Method method : target.getClass().getMethods()) {
            AgentTool annotation = method.getAnnotation(AgentTool.class);
            if (annotation != null) {
                tools.add(new MethodTool(target, method, annotation));
            }
        }
        return List.copyOf(tools);
    }

    private static final class MethodTool implements Tool {

        private final Object target;
        private final Method method;
        private final ToolSpec spec;

        MethodTool(Object target, Method method, AgentTool annotation) {
            this.target = target;
            this.method = method;
            String name = annotation.name().isBlank() ? method.getName() : annotation.name();
            this.spec = new ToolSpec(name, annotation.description(), schemaFor(method), annotation.effect());
        }

        @Override
        public ToolSpec spec() {
            return spec;
        }

        @Override
        public ToolResult invoke(String argumentsJson) {
            Object[] args;
            try {
                args = bind(argumentsJson);
            } catch (IllegalArgumentException e) {
                // Bad/mismatched arguments: report safely so the model can correct and retry.
                return ToolResult.error("invalid arguments for '" + spec.name() + "': " + e.getMessage());
            }
            try {
                return toResult(method.invoke(target, args));
            } catch (InvocationTargetException e) {
                // The tool itself threw: keep its internal detail out of the model's context.
                log.warn("tool '{}' threw", spec.name(), e.getCause());
                return ToolResult.error("tool '" + spec.name() + "' failed");
            } catch (IllegalAccessException | RuntimeException e) {
                log.warn("could not invoke tool '{}'", spec.name(), e);
                return ToolResult.error("tool '" + spec.name() + "' failed");
            }
        }

        private Object[] bind(String argumentsJson) {
            JsonNode root = parse(argumentsJson);
            Parameter[] params = method.getParameters();
            Object[] args = new Object[params.length];
            for (int i = 0; i < params.length; i++) {
                Parameter p = params[i];
                String name = paramName(p);
                JsonNode field = root.get(name);
                if (field == null || field.isNull()) {
                    if (isRequired(p)) {
                        throw new IllegalArgumentException("missing required parameter '" + name + "'");
                    }
                    args[i] = null;
                } else {
                    JavaType jt = MAPPER.constructType(p.getParameterizedType());
                    args[i] = MAPPER.convertValue(field, jt); // IllegalArgumentException on type mismatch
                }
            }
            return args;
        }

        private ToolResult toResult(Object result) {
            if (method.getReturnType() == void.class || result == null) {
                return ToolResult.ok("");
            }
            if (result instanceof ToolResult tr) {
                return tr;
            }
            if (result instanceof String s) {
                return ToolResult.ok(s);
            }
            try {
                return ToolResult.ok(MAPPER.writeValueAsString(result));
            } catch (JsonProcessingException e) {
                log.warn("could not serialize result of tool '{}'", spec.name(), e);
                return ToolResult.error("tool '" + spec.name() + "' produced an unserializable result");
            }
        }
    }

    private static JsonNode parse(String argumentsJson) {
        try {
            String json = (argumentsJson == null || argumentsJson.isBlank()) ? "{}" : argumentsJson;
            return MAPPER.readTree(json);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("arguments were not valid JSON");
        }
    }

    private static String schemaFor(Method method) {
        ObjectNode schema = MAPPER.createObjectNode();
        schema.put("type", "object");
        ObjectNode properties = schema.putObject("properties");
        ArrayNode required = MAPPER.createArrayNode();
        for (Parameter p : method.getParameters()) {
            String name = paramName(p);
            ObjectNode propSchema = typeSchema(p.getParameterizedType());
            ToolParam tp = p.getAnnotation(ToolParam.class);
            if (tp != null && !tp.description().isBlank()) {
                propSchema.put("description", tp.description());
            }
            properties.set(name, propSchema);
            if (isRequired(p)) {
                required.add(name);
            }
        }
        if (!required.isEmpty()) {
            schema.set("required", required);
        }
        return schema.toString();
    }

    private static ObjectNode typeSchema(Type type) {
        if (type instanceof ParameterizedType pt && pt.getRawType() instanceof Class<?> raw) {
            if (Collection.class.isAssignableFrom(raw)) {
                ObjectNode array = MAPPER.createObjectNode();
                array.put("type", "array");
                array.set("items", typeSchema(pt.getActualTypeArguments()[0]));
                return array;
            }
            return classSchema(raw);
        }
        if (type instanceof Class<?> c) {
            return classSchema(c);
        }
        return MAPPER.createObjectNode().put("type", "object");
    }

    private static ObjectNode classSchema(Class<?> c) {
        ObjectNode node = MAPPER.createObjectNode();
        if (STRING_TYPES.contains(c)) {
            node.put("type", "string");
        } else if (c == boolean.class || c == Boolean.class) {
            node.put("type", "boolean");
        } else if (INTEGER_TYPES.contains(c)) {
            node.put("type", "integer");
        } else if (NUMBER_TYPES.contains(c)) {
            node.put("type", "number");
        } else if (c.isEnum()) {
            node.put("type", "string");
            ArrayNode values = node.putArray("enum");
            for (Object constant : c.getEnumConstants()) {
                values.add(((Enum<?>) constant).name());
            }
        } else if (c.isArray()) {
            node.put("type", "array");
            node.set("items", classSchema(c.getComponentType()));
        } else if (c.isRecord()) {
            node.put("type", "object");
            ObjectNode props = node.putObject("properties");
            ArrayNode required = MAPPER.createArrayNode();
            for (RecordComponent rc : c.getRecordComponents()) {
                props.set(rc.getName(), typeSchema(rc.getGenericType()));
                required.add(rc.getName());
            }
            node.set("required", required);
        } else {
            node.put("type", "object"); // unknown POJO: advertise permissively
        }
        return node;
    }

    private static String paramName(Parameter p) {
        ToolParam tp = p.getAnnotation(ToolParam.class);
        return (tp != null && !tp.name().isBlank()) ? tp.name() : p.getName();
    }

    private static boolean isRequired(Parameter p) {
        ToolParam tp = p.getAnnotation(ToolParam.class);
        return tp == null || tp.required();
    }
}
