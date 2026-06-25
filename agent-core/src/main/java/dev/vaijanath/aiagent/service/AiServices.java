package dev.vaijanath.aiagent.service;

import dev.vaijanath.aiagent.agent.Agent;
import dev.vaijanath.aiagent.agent.AgentRequest;
import dev.vaijanath.aiagent.agent.AgentResponse;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.Proxy;
import java.util.Objects;

/**
 * Turns a plain Java interface into an agent-backed implementation — the declarative "AI service"
 * style (à la LangChain4j's {@code @AiService}), but as a thin typed facade over an existing,
 * already-governed {@link Agent} rather than building one. Each interface method becomes one agent
 * turn: its arguments form the input and the agent's answer is returned.
 *
 * <pre>{@code
 * interface SupportBot {
 *     String answer(String question);
 *
 *     @UserMessage("Summarize in {{words}} words:\n{{text}}")
 *     String summarize(@V("text") String text, @V("words") int words);
 * }
 *
 * SupportBot bot = AiServices.create(SupportBot.class, governedAgent);
 * String reply = bot.answer("How do refunds work?");
 * }</pre>
 *
 * <p>A method annotated with {@link UserMessage} renders that template from its {@link V} parameters;
 * otherwise the method must take exactly one argument, whose value is the input. Methods return
 * {@link String} (the agent's output) or {@link AgentResponse} (the full result). {@code default}
 * methods run as written, and {@code toString}/{@code equals}/{@code hashCode} behave normally —
 * neither calls the agent. Calls are stateless (a fresh session each); for conversation memory, use
 * the {@link Agent} directly with a stable session id.
 */
public final class AiServices {

    private AiServices() {}

    /** Creates an implementation of {@code serviceInterface} backed by {@code agent}. */
    public static <T> T create(Class<T> serviceInterface, Agent agent) {
        Objects.requireNonNull(serviceInterface, "serviceInterface");
        Objects.requireNonNull(agent, "agent");
        if (!serviceInterface.isInterface()) {
            throw new IllegalArgumentException(serviceInterface + " is not an interface");
        }
        Object proxy = Proxy.newProxyInstance(
                serviceInterface.getClassLoader(), new Class<?>[] {serviceInterface}, new Handler(agent));
        return serviceInterface.cast(proxy);
    }

    private record Handler(Agent agent) implements InvocationHandler {

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (method.isDefault()) {
                return InvocationHandler.invokeDefault(proxy, method, args);
            }
            if (method.getDeclaringClass() == Object.class) {
                return objectMethod(proxy, method, args);
            }
            Class<?> returnType = method.getReturnType();
            if (returnType != String.class && returnType != AgentResponse.class) {
                throw new IllegalStateException(
                        "AiService method '" + method.getName() + "' must return String or AgentResponse");
            }
            AgentResponse response = agent.run(new AgentRequest(buildInput(method, args)));
            return returnType == AgentResponse.class ? response : response.output();
        }

        private static String buildInput(Method method, Object[] args) {
            UserMessage template = method.getAnnotation(UserMessage.class);
            if (template != null) {
                return render(template.value(), method, args);
            }
            if (args == null || args.length != 1) {
                throw new IllegalStateException("AiService method '" + method.getName()
                        + "' needs a @UserMessage template or exactly one argument");
            }
            return String.valueOf(args[0]);
        }

        private static String render(String template, Method method, Object[] args) {
            String result = template;
            Parameter[] params = method.getParameters();
            for (int i = 0; i < params.length; i++) {
                V var = params[i].getAnnotation(V.class);
                if (var != null) {
                    result = result.replace("{{" + var.value() + "}}", String.valueOf(args[i]));
                }
            }
            return result;
        }

        private Object objectMethod(Object proxy, Method method, Object[] args) {
            return switch (method.getName()) {
                case "toString" -> "AiService(" + agent + ")";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == (args == null ? null : args[0]);
                default -> throw new IllegalStateException("unexpected Object method: " + method);
            };
        }
    }
}
