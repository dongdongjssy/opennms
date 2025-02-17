/*
 * Licensed to The OpenNMS Group, Inc (TOG) under one or more
 * contributor license agreements.  See the LICENSE.md file
 * distributed with this work for additional information
 * regarding copyright ownership.
 *
 * TOG licenses this file to You under the GNU Affero General
 * Public License Version 3 (the "License") or (at your option)
 * any later version.  You may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at:
 *
 *      https://www.gnu.org/licenses/agpl-3.0.txt
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the
 * License.
 */
package org.opennms.core.profiler;

import java.util.Objects;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.opennms.core.logging.Logging;
import org.slf4j.LoggerFactory;

import com.google.common.base.Throwables;

/**
 * Aspect to measure the execution time of methods.
 * Simply annotate the class or method to measure with {@link Profile}.
 * In order to get this to work, a bean of type {@link ProfilerAspect} must be in the current Spring Application Context
 * and AOP must be enabled, e.g. <aop:aspectj-autoproxy/>.
 */
@Aspect
public class ProfilerAspect {

    public interface Block<T> {
        T execute() throws Exception;
    }

    @Around("@within(profile) || @annotation(profile)")
    public Object logAroundByMethod(ProceedingJoinPoint joinPoint, Profile profile) throws Throwable {
        Timer timer = new Timer();
        try {
            timer.start();
            return joinPoint.proceed();
        } finally {
            log(joinPoint.getKind(), joinPoint.getSignature().toShortString(), timer.stop());
        }
    }

    /**
     * Sometimes adding an annotation may not work, e.g. the class is not managed by spring.
     * This method allows to wrap any statements to be measured, by simply wrap it into a {@link Block} object
     *
     * @return the return of the block. If no return is required, simply return null.
     */
    public static <T> T wrapProfile(Class<?> clazz, String signature, Block<T> block) {
        return wrapProfile(clazz.getSimpleName() + "." + signature, block);
    }

    private static <T> T wrapProfile(String signature, Block<T> block) {
        Objects.requireNonNull(signature);
        Objects.requireNonNull(block);
        Timer timer = new Timer();
        try {
            timer.start();
            return block.execute();
        } catch (Exception e) {
            throw Throwables.propagate(e);
        } finally {
            log("method-execution", signature, timer.stop());
        }
    }

    /**
     * Logs the execution time of the @Profile annotated method.
     *
     * @param kind The kind, e.g. "method-execution"
     * @param signature The method signature
     * @param executionTime The execution time in ms.
     */
    private static void log(String kind, String signature, long executionTime) {
        Logging.withPrefix("profiler", () -> LoggerFactory.getLogger(ProfilerAspect.class).info("{} {} took {}, raw = {}ms", signature, kind, humanReadable(executionTime), executionTime));
    }

    /**
     * Converts the input milliseconds in a human readable format (e.g. 1s 100ms)
     *
     * @param milliseconds The ms to convert
     * @return The human readable format, e.g. 2h 15m 3s 150ms
     */
    public static String humanReadable(final long milliseconds) {
        int seconds = (int)(milliseconds / 1000) % 60 ;
        int minutes = (int) ((milliseconds / (1000*60)) % 60);
        int hours   = (int) ((milliseconds / (1000*60*60)) % 24);

        if (hours > 0) {
            long ms = milliseconds - hours * 1000 * 60 * 60 - minutes * 1000 * 60 - seconds * 1000;
            return String.format("%dh %dm %ds %dms", hours, minutes, seconds, ms);
        }
        if (minutes > 0) {
            long ms = milliseconds - minutes * 1000 * 60 - seconds * 1000;
            return String.format("%dm %ds %dms", minutes, seconds, ms);
        }
        if (seconds > 0) {
            long ms = milliseconds - seconds * 1000;
            return String.format("%ds %dms", seconds, ms);
        }
        return String.format("%dms", milliseconds);
    }
}

