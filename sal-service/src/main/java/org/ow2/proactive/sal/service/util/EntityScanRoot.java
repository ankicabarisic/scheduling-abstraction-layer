/*
 * ProActive Parallel Suite(TM):
 * The Open Source library for parallel and distributed
 * Workflows & Scheduling, Orchestration, Cloud Automation
 * and Big Data Analysis on Enterprise Grids & Clouds.
 *
 * Copyright (c) 2007 - 2017 ActiveEon
 * Contact: contact@activeeon.com
 *
 * This library is free software: you can redistribute it and/or
 * modify it under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation: version 3 of
 * the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 *
 * If needed, contact us to obtain a release under GPL Version 2 or 3
 * or a different license than the AGPL.
 */
package org.ow2.proactive.sal.service.util;

import java.lang.annotation.*;

import org.springframework.context.annotation.Import;


/**
 * Allows to override the entity scan root
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(EntityScanRootRegistrar.class)
public @interface EntityScanRoot {

    /**
     * Override the default entity scan root ("classpath:") to a more accurate path.
     * This expression is parsed by a PathMatchingResourcePatternResolver.
     *
     * Supports wild card notation e.g. classpath*:path/somejar*.jar. The wildcard must return a single resource
     *
     * @see org.springframework.core.io.support.PathMatchingResourcePatternResolver#getResource(String)
     * @see org.springframework.core.io.support.PathMatchingResourcePatternResolver#getResources(String)
     *
     * @return the entity scan classpath string
     */
    String value() default "classpath:";

}
