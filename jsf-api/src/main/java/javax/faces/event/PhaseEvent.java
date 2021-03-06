/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 1997-2015 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://glassfish.java.net/public/CDDL+GPL_1_1.html
 * or packager/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at packager/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * Oracle designates this particular file as subject to the "Classpath"
 * exception as provided by Oracle in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */

package javax.faces.event;


import java.util.EventObject;
import javax.faces.context.FacesContext;
import javax.faces.lifecycle.Lifecycle;


/**
 * <p><strong>PhaseEvent</strong> represents the beginning or ending of
 * processing for a particular phase of the request processing lifecycle,
 * for the request encapsulated by the specified {@link FacesContext}.</p>
 */

public class PhaseEvent extends EventObject {

    // ----------------------------------------------------------- Constructors


    private static final long serialVersionUID = 7603034985956521464L;

    /**
     * <p>Construct a new event object from the specified parameters.
     * The specified {@link Lifecycle} will be the source of this event.</p>
     *
     * @param context {@link FacesContext} for the current request
     * @param phaseId Identifier of the current request processing
     *  lifecycle phase
     * @param lifecycle Lifecycle instance
     *
     * @throws NullPointerException if <code>context</code> or
     *  <code>phaseId</code> or <code>Lifecycle</code>is <code>null</code>
     */
    public PhaseEvent(FacesContext context, PhaseId phaseId, 
            Lifecycle lifecycle) {

        super(lifecycle);
        if ( phaseId == null || context == null || lifecycle == null) {
            throw new NullPointerException();
        }
	this.phaseId = phaseId;
        this.context = context;

    }


    // ------------------------------------------------------------- Properties

    private FacesContext context = null;
    
    /**
     * <p>Return the {@link FacesContext} for the request being processed.</p>
     *
     * @return the {@code FacesContext} for the current request.
     *
     */
    public FacesContext getFacesContext() {

        return context;

    }


    private PhaseId phaseId = null;


    /**
     * <p>Return the {@link PhaseId} representing the current request
     * processing lifecycle phase.</p>
     *
     * @return the phase id
     */
    public PhaseId getPhaseId() {

	return (this.phaseId);

    }


}
