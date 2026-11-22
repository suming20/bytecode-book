package cn.suming;

import cn.suming.interceptor.TimeInterceptor;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;
import net.bytebuddy.utility.JavaModule;

import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;

/**
 * @author suming
 * @since 2026/11/22 13:44
 */
public class AgentByteBuddy {

    public static void premain(String args, Instrumentation inst) {
        // 构建增强
        AgentBuilder.Transformer transformer = new AgentBuilder.Transformer() {
            @Override
            public DynamicType.Builder<?> transform(DynamicType.Builder<?> builder, TypeDescription typeDescription,
                                                    ClassLoader classLoader, JavaModule module,
                                                    ProtectionDomain protectionDomain) {
                return builder.method(ElementMatchers.any()).intercept(MethodDelegation.to(TimeInterceptor.class));
            }
        };
        // 对哪些对象增强

        new AgentBuilder.Default().type(ElementMatchers.nameStartsWith("cn.suming"))
                .transform(transformer).installOn(inst);

    }
}
