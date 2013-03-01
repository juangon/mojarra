/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 1997-2013 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://glassfish.dev.java.net/public/CDDL+GPL_1_1.html
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

package com.sun.faces.application;

import com.sun.faces.config.InitFacesContext;
import com.sun.faces.application.view.ViewScopeManager;
import javax.faces.FacesException;
import javax.faces.application.NavigationCase;
import javax.faces.application.ViewHandler;
import javax.faces.component.UIViewRoot;
import javax.faces.context.ExternalContext;
import javax.faces.context.FacesContext;
import javax.faces.context.PartialViewContext;
import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.logging.Level;
import java.util.logging.Logger;
import com.sun.faces.util.MessageUtils;
import com.sun.faces.util.Util;
import com.sun.faces.util.FacesLogger;
import java.util.concurrent.ConcurrentHashMap;
import javax.el.ELContext;
import javax.el.MethodExpression;
import javax.el.ValueExpression;
import javax.faces.application.ConfigurableNavigationHandler;
import javax.faces.application.FacesMessage;
import javax.faces.component.UIViewAction;
import javax.faces.context.Flash;
import javax.faces.flow.FlowCallNode;
import javax.faces.flow.Flow;
import javax.faces.flow.FlowHandler;
import javax.faces.flow.FlowNode;
import javax.faces.flow.MethodCallNode;
import javax.faces.flow.Parameter;
import javax.faces.flow.ReturnNode;
import javax.faces.flow.SwitchCase;
import javax.faces.flow.SwitchNode;
import javax.faces.flow.ViewNode;

/**
 * <p><strong>NavigationHandlerImpl</strong> is the class that implements
 * default navigation handling. Refer to section 7.4.2 of the specification for
 * more details.
 * PENDING: Make independent of ApplicationAssociate. 
 */

public class NavigationHandlerImpl extends ConfigurableNavigationHandler {

    // Log instance for this class
    private static final Logger logger = FacesLogger.APPLICATION.getLogger();

    /**
     * <code>Map</code> containing configured navigation cases.
     */
    private volatile Map<String, NavigationInfo> navigationMaps;


    /**
     * Flag indicated the current mode.
     */
    private boolean development;
    private static final Pattern REDIRECT_EQUALS_TRUE = Pattern.compile("(.*)(faces-redirect=true)(.*)");
    private static final Pattern INCLUDE_VIEW_PARAMS_EQUALS_TRUE = Pattern.compile("(.*)(includeViewParams=true)(.*)");


    // ------------------------------------------------------------ Constructors


    /**
     * This constructor uses the current <code>ApplicationAssociate</code>
     * instance to obtain the navigation mappings used to make
     * navigational decisions.
     */
    public NavigationHandlerImpl() {

        super();
        if (logger.isLoggable(Level.FINE)) {
            logger.log(Level.FINE, "Created NavigationHandler instance ");
        }
        ApplicationAssociate associate = ApplicationAssociate.getInstance(
              FacesContext.getCurrentInstance().getExternalContext());
        if (associate != null) {
            development = associate.isDevModeEnabled();
        }

    }


    // ------------------------------ Methods from ConfigurableNavigationHandler


    /**
     * @see javax.faces.application.ConfigurableNavigationHandler#getNavigationCase(javax.faces.context.FacesContext, String, String)
     */
    @Override
    public NavigationCase getNavigationCase(FacesContext context, String fromAction, String outcome) {

        return getNavigationCase(context, fromAction, outcome, "");
        
    }

    @Override
    public NavigationCase getNavigationCase(FacesContext context, String fromAction, String outcome, String toFlowDocumentId) {
        Util.notNull("context", context);
        Util.notNull("toFlowDocumentId", toFlowDocumentId);
        NavigationCase result = null;
        CaseStruct caseStruct = getViewId(context, fromAction, outcome, toFlowDocumentId);
        if (null != caseStruct) {
            result = caseStruct.navCase;
        }
        
        return result;
        
    }
    
    


    /**
     * @see javax.faces.application.ConfigurableNavigationHandler#getNavigationCases()
     */
    @Override
    public Map<String, Set<NavigationCase>> getNavigationCases() {
        FacesContext context = FacesContext.getCurrentInstance();
        Map<String, Set<NavigationCase>> result = getNavigationMap(context);

        return result;

    }

    @Override
    public void inspectFlow(FacesContext context, Flow flow) {
        initializeNavigationFromFlow(context, flow);
    }

    // ------------------------------------------ Methods from NavigationHandler
    @Override
    public void handleNavigation(FacesContext context,
                                 String fromAction,
                                 String outcome) {
        this.handleNavigation(context, fromAction, outcome, "");
    }

    @Override
    public void handleNavigation(FacesContext context, String fromAction, String outcome, String toFlowDocumentId) {
        Util.notNull("context", context);

        CaseStruct caseStruct = getViewId(context, fromAction, outcome, toFlowDocumentId);
        if (caseStruct != null) {
            ExternalContext extContext = context.getExternalContext();
            ViewHandler viewHandler = Util.getViewHandler(context);
            assert (null != viewHandler);
            Flash flash = extContext.getFlash();
            boolean isUIViewActionBroadcastAndViewdsDiffer = false;
            if (UIViewAction.isProcessingBroadcast(context)) {
                flash.setKeepMessages(true);
                String viewIdBefore = context.getViewRoot().getViewId();
                viewIdBefore = (null == viewIdBefore) ? "" : viewIdBefore;
                String viewIdAfter = caseStruct.navCase.getToViewId(context);
                viewIdAfter = (null == viewIdAfter) ? "" : viewIdAfter;
                isUIViewActionBroadcastAndViewdsDiffer = !viewIdBefore.equals(viewIdAfter);
            } 
            clearViewMapIfNecessary(context, caseStruct.viewId);
            if (caseStruct.navCase.isRedirect() || isUIViewActionBroadcastAndViewdsDiffer) {
                
                // PENDING(edburns): Flows currently don't work with redirect.
                // Obviously I have to fix that.

                // perform a 302 redirect.
                String redirectUrl =
                      viewHandler.getRedirectURL(context,
                                                 caseStruct.viewId,
                                                 SharedUtils.evaluateExpressions(context, caseStruct.navCase.getParameters()),
                                                 caseStruct.navCase.isIncludeViewParams());
                try {
                    if (logger.isLoggable(Level.FINE)) {
                        logger.log(Level.FINE, "Redirecting to path {0} for outcome {1}and viewId {2}", new Object[]{redirectUrl, outcome, caseStruct.viewId});
                    }
                    // encode the redirect to ensure session state
                    // is maintained
                    updateRenderTargets(context, caseStruct.viewId);
                    flash.setRedirect(true);
                    extContext.redirect(redirectUrl);
                } catch (java.io.IOException ioe) {
                    if (logger.isLoggable(Level.FINE)) {
                        logger.log(Level.FINE,"jsf.redirect_failed_error",
                                   redirectUrl);
                    }
                    throw new FacesException(ioe.getMessage(), ioe);
                }
                context.responseComplete();
               if (logger.isLoggable(Level.FINE)) {
                   logger.log(Level.FINE, "Response complete for {0}", caseStruct.viewId);
               }
            } else {
                UIViewRoot newRoot = viewHandler.createView(context,
                                                            caseStruct.viewId);
                updateRenderTargets(context, caseStruct.viewId);
                context.setViewRoot(newRoot);
                FlowHandler flowHandler = context.getApplication().getFlowHandler();
                if (null != flowHandler) {
                    flowHandler.transition(context, 
                            caseStruct.currentFlow, caseStruct.newFlow, 
                            caseStruct.facesFlowCallNode, caseStruct.viewId);
                }
                if (logger.isLoggable(Level.FINE)) {
                    logger.log(Level.FINE, "Set new view in FacesContext for {0}", caseStruct.viewId);
                }
            }
        } 
    }
    
    // --------------------------------------------------------- Private Methods
    private static final String ROOT_NAVIGATION_MAP_ID = NavigationHandlerImpl.class.getName() + ".NAVIGATION_MAP";
    
    private Map<String, Set<NavigationCase>> getNavigationMap(FacesContext context) {
        Map<String, Set<NavigationCase>> result = null;
        NavigationInfo info = null;
        if (null == navigationMaps) {
            createNavigationMaps();
            result = navigationMaps.get(ROOT_NAVIGATION_MAP_ID).ruleSet;
        } else {
            FlowHandler fh = context.getApplication().getFlowHandler();
            if (null != fh) {
                Flow currentFlow = fh.getCurrentFlow(context);
                if (null != currentFlow) {
                    info = navigationMaps.get(currentFlow.getDefiningDocumentId() + currentFlow.getId());
                    // We are in a flow, but there are no navigation rules for 
                    // this flow.
                    if (null == info) {
                        return Collections.emptyMap();
                    }
                }
            }
            if (null == info) {
                info = navigationMaps.get(ROOT_NAVIGATION_MAP_ID);
            }
            if (null == info.ruleSet) {
                result = Collections.emptyMap();
            } else {
                result = info.ruleSet;
            }
        }
        
        return result;
    }
    
    private void createNavigationMaps() {
        if (null == navigationMaps) {
            NavigationMap result = null;
            NavigationInfo info = null;
            navigationMaps = new ConcurrentHashMap<String, NavigationInfo>();
            result = new NavigationMap();
            info = new NavigationInfo();
            info.ruleSet = result;
            navigationMaps.put(ROOT_NAVIGATION_MAP_ID, info);
        }
    }
    
    private Map<String, Set<NavigationCase>> getRootNavigationMap() {
        createNavigationMaps();
        return navigationMaps.get(ROOT_NAVIGATION_MAP_ID).ruleSet;
    }
    
    private Set<String> getWildCardMatchList(FacesContext context) {
        Set<String> result = Collections.emptySet();
        NavigationInfo info = null;
        FlowHandler fh = context.getApplication().getFlowHandler();
        if (null != fh) {
            Flow currentFlow = fh.getCurrentFlow(context);
            if (null != currentFlow) {
                info = navigationMaps.get(currentFlow.getDefiningDocumentId() + currentFlow.getId());
            }
        }
        if (null == info) {
            info = navigationMaps.get(ROOT_NAVIGATION_MAP_ID);
        }
        if (null != info.ruleSet && null != info.ruleSet.wildcardMatchList) {
            result = info.ruleSet.wildcardMatchList;
        }
        return result;
    }
    
    private NavigationInfo getNavigationInfo(FacesContext context, String toFlowDocumentId, String flowId) {
        NavigationInfo result = null;
        assert(null != navigationMaps);
        result = navigationMaps.get(toFlowDocumentId + flowId);
        if (null == result) {
            FlowHandler fh = context.getApplication().getFlowHandler();
            if (null != fh) {
                Flow currentFlow = fh.getCurrentFlow(context);
                if (null != currentFlow) {
                    result = navigationMaps.get(currentFlow.getDefiningDocumentId() + currentFlow.getId());
                }
            }
        }
        
        return result;
    }

    private void initializeNavigationFromAssociate() {

        ApplicationAssociate associate = ApplicationAssociate.getCurrentInstance();
        if (associate != null) {
            Map<String,Set<NavigationCase>> m = associate.getNavigationCaseListMappings();
            Map<String, Set<NavigationCase>> rootMap = getRootNavigationMap();
            if (m != null) {
                rootMap.putAll(m);
            }
        }

    }
    
    private void initializeNavigationFromFlow(FacesContext context, Flow toInspect) {
        
        if (context instanceof InitFacesContext) {
            createNavigationMaps();
            initializeNavigationFromFlowNonThreadSafe(toInspect);
        } else {
            // PENDING: When JAVASERVERFACES-2580 is done, the eager case will
            // no longer be necessary and can be removed.

            assert(null != navigationMaps);
            initializeNavigationFromFlowThreadSafe(toInspect);
        }
        
    }
    
    private void initializeNavigationFromFlowNonThreadSafe(Flow toInspect) {
        String fullyQualifiedFlowId = toInspect.getDefiningDocumentId() + toInspect.getId();
        // Is there an existing NavigationMap for this flowId
        if (navigationMaps.containsKey(fullyQualifiedFlowId)) {
            if (logger.isLoggable(Level.INFO)) {
                logger.log(Level.INFO, "PENDING(edburns): merge existing map");
            }
            
        } else {
            Map<String, Set<NavigationCase>> navRules = toInspect.getNavigationCases();
            Map<String, SwitchNode> switches = toInspect.getSwitches();

            if (!navRules.isEmpty() || !switches.isEmpty()) {
                NavigationInfo info = new NavigationInfo();
                if (!switches.isEmpty()) {
                    info.switches = new ConcurrentHashMap<String, SwitchNode>();
                    for (Map.Entry<String, SwitchNode> cur : switches.entrySet()) {
                        info.switches.put(cur.getKey(), cur.getValue());
                    }
                }
                if (!navRules.isEmpty()) {
                    info.ruleSet = new NavigationMap();
                    info.ruleSet.putAll(navRules);
                }
                navigationMaps.put(fullyQualifiedFlowId, info);
            }
        }
        
    }
    
    private void initializeNavigationFromFlowThreadSafe(Flow toInspect) {
        synchronized (this) {
            initializeNavigationFromFlowNonThreadSafe(toInspect);
        }
    }
    
    /**
     * Calls <code>clear()</code> on the ViewMap (if available) if the view
     * ID of the UIViewRoot differs from <code>newId</code>
     */
    private void clearViewMapIfNecessary(FacesContext facesContext, String newId) {
        UIViewRoot root = facesContext.getViewRoot();

        if (root != null && !root.getViewId().equals(newId)) {
            Map<String, Object> viewMap = root.getViewMap(false);
            if (viewMap != null) {
                ViewScopeManager manager = ViewScopeManager.getInstance(facesContext);
                manager.clear(facesContext);
            }
        }
    }
    
    private void updateRenderTargets(FacesContext ctx, String newId) {

        if (ctx.getViewRoot() == null || !ctx.getViewRoot().getViewId().equals(newId)) {
            PartialViewContext pctx = ctx.getPartialViewContext();
            if (!pctx.isRenderAll()) {
                pctx.setRenderAll(true);
            }
        }

    }


    /**
     * This method uses helper methods to determine the new <code>view</code> identifier.
     * Refer to section 7.4.2 of the specification for more details.
     *
     * @param ctx the @{link FacesContext} for the current request
     * @param fromAction The action reference string
     * @param outcome    The outcome string
     * @return The <code>view</code> identifier.
     */
    private CaseStruct getViewId(FacesContext ctx,
                                 String fromAction,
                                 String outcome, String toFlowDocumentId) {

        if (navigationMaps == null) {
            synchronized (this) {
                initializeNavigationFromAssociate();
            }
        }

        UIViewRoot root = ctx.getViewRoot();

        
        String viewId = (root != null ? root.getViewId() : null);
        
        // if viewIdToTest is not null, use its value to find
        // a navigation match, otherwise look for a match
        // based soley on the fromAction and outcome
        CaseStruct caseStruct = null;
        if (viewId != null) {
            caseStruct = findExactMatch(ctx, viewId, fromAction, outcome, toFlowDocumentId);

            if (caseStruct == null) {
                caseStruct = findWildCardMatch(ctx, viewId, fromAction, outcome, toFlowDocumentId);
            }
        }

        if (caseStruct == null) {
            caseStruct = findDefaultMatch(ctx, fromAction, outcome, toFlowDocumentId);
        }
        
        // If the preceding steps found a match, but it was a flow call...
        if (null != caseStruct && caseStruct.isFlowEntryFromExplicitRule) {
            // Override the toFlowDocumentId with the value from the navigation-case, if present
            toFlowDocumentId = (null != caseStruct.navCase.getToFlowDocumentId()) ? caseStruct.navCase.getToFlowDocumentId() : toFlowDocumentId;
            // and try to call into the flow
            caseStruct = findFacesFlowCallMatch(ctx, fromAction, outcome, toFlowDocumentId);
        }
        
        // If we still don't have a match, see if this is a switch
        if (null == caseStruct && null != fromAction && null != outcome) {
            caseStruct = findSwitchMatch(ctx, fromAction, outcome, toFlowDocumentId);
        }

        // If we still don't have a match, see if this is a method-call
        if (null == caseStruct && null != fromAction && null != outcome) {
            caseStruct = findMethodCallMatch(ctx, fromAction, outcome);
        }
        
        // If we still don't have a match, see if this is a faces-flow-call
        if (null == caseStruct && null != outcome) {
            caseStruct = findFacesFlowCallMatch(ctx, fromAction, outcome, toFlowDocumentId);
        }

        // If we still don't have a match, see if this is a flow-return
        if (null == caseStruct && null != outcome) {
            caseStruct = findReturnMatch(ctx, fromAction, outcome);
        }

        // If the navigation rules do not have a match...
        if (caseStruct == null && outcome != null && viewId != null) {
            // Treat empty string equivalent to null outcome.  JSF 2.0 Rev a
            // Changelog issue C063.
            if (0 == outcome.length()) {
                outcome = null;
            } else {
                caseStruct = findImplicitMatch(ctx, viewId, fromAction, outcome,
                        toFlowDocumentId);
            }
        }
        
        // no navigation case fo
        if (caseStruct == null && outcome != null && development) {
            String key;
            Object[] params;
            if (fromAction == null) {
                key = MessageUtils.NAVIGATION_NO_MATCHING_OUTCOME_ID;
                params = new Object[] { viewId, outcome };
            } else {
                key = MessageUtils.NAVIGATION_NO_MATCHING_OUTCOME_ACTION_ID;
                params = new Object[] { viewId, fromAction, outcome };
            }
            FacesMessage m = MessageUtils.getExceptionMessage(key, params);
            m.setSeverity(FacesMessage.SEVERITY_WARN);
            ctx.addMessage(null, m);
        }
        return caseStruct;
    }


    /**
     * This method finds the List of cases for the current <code>view</code> identifier.
     * After the cases are found, the <code>from-action</code> and <code>from-outcome</code>
     * values are evaluated to determine the new <code>view</code> identifier.
     * Refer to section 7.4.2 of the specification for more details.
     *
     * @param ctx the {@link FacesContext} for the current request
     * @param viewId     The current <code>view</code> identifier.
     * @param fromAction The action reference string.
     * @param outcome    The outcome string.
     * @return The <code>view</code> identifier.
     */
    private CaseStruct findExactMatch(FacesContext ctx,
                                      String viewId,
                                      String fromAction,
                                      String outcome, String toFlowDocumentId) {
        Map<String, Set<NavigationCase>> navMap = getNavigationMap(ctx);

        Set<NavigationCase> caseSet = navMap.get(viewId);

        if (caseSet == null) {
            return null;
        }

        // We've found an exact match for the viewIdToTest.  Now we need to evaluate
        // from-action/outcome in the following order:
        // 1) elements specifying both from-action and from-outcome
        // 2) elements specifying only from-outcome
        // 3) elements specifying only from-action
        // 4) elements where both from-action and from-outcome are null
        
        CaseStruct result = determineViewFromActionOutcome(ctx, caseSet, fromAction, outcome, toFlowDocumentId);
        if (null != result) {
            FlowHandler flowHandler = ctx.getApplication().getFlowHandler();
            if (null != flowHandler) {
                result.currentFlow = flowHandler.getCurrentFlow(ctx);
                result.newFlow = result.currentFlow;
            }
        }

        return result;
    }


    /**
     * This method traverses the wild card match List (containing <code>from-view-id</code>
     * strings and finds the List of cases for each <code>from-view-id</code> string.
     * Refer to section 7.4.2 of the specification for more details.
     *
     * @param ctx the {@link FacesContext} for the current request
     * @param viewId     The current <code>view</code> identifier.
     * @param fromAction The action reference string.
     * @param outcome    The outcome string.
     * @return The <code>view</code> identifier.
     */

    private CaseStruct findWildCardMatch(FacesContext ctx,
                                         String viewId,
                                         String fromAction,
                                         String outcome, String toFlowDocumentId) {
        CaseStruct result = null;
        Map<String, Set<NavigationCase>> navMap = getNavigationMap(ctx);

        for (String fromViewId : getWildCardMatchList(ctx)) {
            // See if the entire wildcard string (without the trailing "*" is
            // contained in the incoming viewIdToTest.  
            // Ex: /foobar is contained with /foobarbaz
            // If so, then we have found our largest pattern match..
            // If not, then continue on to the next case;

            if (!viewId.startsWith(fromViewId)) {
                continue;
            }

            // Append the trailing "*" so we can do our map lookup;

            String wcFromViewId = new StringBuilder(32).append(fromViewId).append('*').toString();
            Set<NavigationCase> ccaseSet = navMap.get(wcFromViewId);

            if (ccaseSet == null) {
                return null;
            }

            // If we've found a match, then we need to evaluate
            // from-action/outcome in the following order:
            // 1) elements specifying both from-action and from-outcome
            // 2) elements specifying only from-outcome
            // 3) elements specifying only from-action
            // 4) elements where both from-action and from-outcome are null

            result = determineViewFromActionOutcome(ctx,
                                                    ccaseSet,
                                                    fromAction,
                                                    outcome, toFlowDocumentId);
            if (result != null) {
                break;
            }
        }
        if (null != result) {
            FlowHandler flowHandler = ctx.getApplication().getFlowHandler();
            if (null != flowHandler) {
                result.currentFlow = flowHandler.getCurrentFlow(ctx);
                result.newFlow = result.currentFlow;
            }
        }
        
        return result;
    }


    /**
     * This method will extract the cases for which a <code>from-view-id</code> is
     * an asterisk "*".
     * Refer to section 7.4.2 of the specification for more details.
     *
     * @param ctx the {@link FacesContext} for the current request
     * @param fromAction The action reference string.
     * @param outcome    The outcome string.
     * @return The <code>view</code> identifier.
     */

    private CaseStruct findDefaultMatch(FacesContext ctx,
                                        String fromAction,
                                        String outcome, String toFlowDocumentId) {
        Map<String, Set<NavigationCase>> navMap = getNavigationMap(ctx);
        
        Set<NavigationCase> caseSet = navMap.get("*");

        if (caseSet == null) {
            return null;
        }

        // We need to evaluate from-action/outcome in the follow
        // order:  1)elements specifying both from-action and from-outcome
        // 2) elements specifying only from-outcome
        // 3) elements specifying only from-action
        // 4) elements where both from-action and from-outcome are null

        CaseStruct result = determineViewFromActionOutcome(ctx, caseSet, fromAction, outcome, toFlowDocumentId);
        
        if (null != result) {
            FlowHandler flowHandler = ctx.getApplication().getFlowHandler();
            if (null != flowHandler) {
                result.currentFlow = flowHandler.getCurrentFlow(ctx);
                result.newFlow = result.currentFlow;
            }
        }
        
        return result;
    }

    /**
     * <p>
     * Create a navigation case based on content within the outcome.
     * </p>
     *
     * @param context the {@link FacesContext} for the current request
     * @param viewId of the {@link UIViewRoot} for the current request
     * @param fromAction the navigation action
     * @param outcome the navigation outcome
     * @return a CaseStruct representing the the navigation result based
     *  on the provided input
     */
    private CaseStruct findImplicitMatch(FacesContext context,
                                         String viewId,
                                         String fromAction,
                                         String outcome,
                                         String flowDefiningDocumentId) {

        // look for an implicit match.
        String viewIdToTest = outcome;
        String currentViewId = viewId;
        Map<String, List<String>> parameters = null;
        boolean isRedirect = false;
        boolean isIncludeViewParams = false;
        CaseStruct result = null;

        int questionMark = viewIdToTest.indexOf('?');
        String queryString;
        if (-1 != questionMark) {
            int viewIdLen = viewIdToTest.length();
            if (viewIdLen <= (questionMark+1)) {
                if (logger.isLoggable(Level.SEVERE)) {
                    logger.log(Level.SEVERE, "jsf.navigation_invalid_query_string",
                            viewIdToTest);
                }
                if (development) {
                    String key;
                    Object[] params;
                    key = MessageUtils.NAVIGATION_INVALID_QUERY_STRING_ID;
                    params = new Object[]{viewIdToTest};
                    FacesMessage m = MessageUtils.getExceptionMessage(key, params);
                    m.setSeverity(FacesMessage.SEVERITY_WARN);
                    context.addMessage(null, m);
                }
                queryString = null;
                viewIdToTest = viewIdToTest.substring(0, questionMark);
            } else {
                queryString = viewIdToTest.substring(questionMark + 1);
                viewIdToTest = viewIdToTest.substring(0, questionMark);

                Matcher m = REDIRECT_EQUALS_TRUE.matcher(queryString);
                if (m.find()) {
                    isRedirect = true;
                    queryString = queryString.replace(m.group(2), "");
                }
                m = INCLUDE_VIEW_PARAMS_EQUALS_TRUE.matcher(queryString);
                if (m.find()) {
                    isIncludeViewParams = true;
                    queryString = queryString.replace(m.group(2), "");
                }
            }

            if (queryString != null && queryString.length() > 0) {
                Map<String, Object> appMap = context.getExternalContext().getApplicationMap();

                String[] queryElements = Util.split(appMap, queryString, "&amp;|&");
                for (int i = 0, len = queryElements.length; i < len; i ++) {
                    String[] elements = Util.split(appMap, queryElements[i], "=");
                    if (elements.length == 2) {
                        if (parameters == null) {
                            parameters = new LinkedHashMap<String,List<String>>(len / 2, 1.0f);
                            List<String> values = new ArrayList<String>(2);
                            values.add(elements[1]);
                            parameters.put(elements[0], values);
                        } else {
                            List<String> values = parameters.get(elements[0]);
                            if (values == null) {
                                values = new ArrayList<String>(2);
                                parameters.put(elements[0], values);
                            }
                            values.add(elements[1]);
                        }
                    }
                }
            }
        }

        // If the viewIdToTest needs an extension, take one from the currentViewId.
        if (viewIdToTest.lastIndexOf('.') == -1) {
            int idx = currentViewId.lastIndexOf('.');
            if (idx != -1) {
                viewIdToTest = viewIdToTest + currentViewId.substring(idx);
            }
        }

        if (!viewIdToTest.startsWith("/")) {
            int lastSlash = currentViewId.lastIndexOf("/");
            if (lastSlash != -1) {
                currentViewId = currentViewId.substring(0, lastSlash + 1);
                viewIdToTest = currentViewId + viewIdToTest;
            } else {
                viewIdToTest = "/" + viewIdToTest;
            }
        }

        ViewHandler viewHandler = Util.getViewHandler(context);
        FlowHandler flowHandler = context.getApplication().getFlowHandler();
        Flow currentFlow = null;
        Flow newFlow = null;

        if (null != flowHandler) {
            
            currentFlow = flowHandler.getCurrentFlow(context);
            newFlow = currentFlow;
            // If we are in a flow, use the implicit rules to ensure the view 
            // is within that flow.  This means viewIdToTest must start with
            // the current flow id
            if (null != currentFlow && null != viewIdToTest && 
                !viewIdToTest.startsWith("/" + currentFlow.getId())) {
                // ... it must be out of the current flow.  Make sure the 
                // current flow is marked as abandoned.
                newFlow = null;
                viewIdToTest = null;
            }            
        }
        if (null != viewIdToTest) {
            viewIdToTest = viewHandler.deriveViewId(context, viewIdToTest);
        }
        
        if (null == result && null != viewIdToTest) {
            result = new CaseStruct();
            result.viewId = viewIdToTest;
            result.navCase = new NavigationCase(currentViewId,
                                                    fromAction,
                                                    outcome,
                                                    null,
                                                    viewIdToTest,
                                                    flowDefiningDocumentId,
                                                    parameters,
                                                    isRedirect,
                                                    isIncludeViewParams);
        }
        if (null != result) {
            result.currentFlow = currentFlow;
            result.newFlow = newFlow;
        }
        

        return result;

    }
    
    private CaseStruct findSwitchMatch(FacesContext context, String fromAction, 
                                       String outcome, String toFlowDocumentId) {
        CaseStruct result = null;
        NavigationInfo info = getNavigationInfo(context, toFlowDocumentId, fromAction);
        FlowHandler flowHandler = context.getApplication().getFlowHandler();
        
        if (null != flowHandler && 
            null != info && null != info.switches && !info.switches.isEmpty()) {
            SwitchNode switchNode = info.switches.get(outcome);
            if (null != switchNode) {
                List<SwitchCase> cases = switchNode.getCases();
                for (SwitchCase cur : cases) {
                    if (cur.getCondition(context)) {
                        outcome = cur.getFromOutcome();
                        Flow newFlow = flowHandler.getFlow(context, toFlowDocumentId, 
                                fromAction);
                        // If this outcome corresponds to an existing flow...
                        if (null != newFlow) {
                            result = synthesizeCaseStruct(context, newFlow, fromAction, outcome);
                        } else {
                            newFlow = flowHandler.getCurrentFlow(context);
                            if (null != newFlow) {
                                result = synthesizeCaseStruct(context, newFlow, fromAction, outcome);
                            }
                        }
                        if (null != result) {
                            break;
                        }
                    }
                }
                if (null == result) {
                    outcome = switchNode.getDefaultOutcome(context);
                    if (null != outcome) {
                        Flow currentFlow = flowHandler.getCurrentFlow(context);
                        if (null != currentFlow) {
                            result = synthesizeCaseStruct(context, currentFlow, fromAction, outcome);
                            if (null != result) {
                                result.currentFlow = currentFlow;
                                result.newFlow = currentFlow;
                            }
                        }
                    }
                }
            }
            
        }
        
        return result;
    }
    
    private CaseStruct synthesizeCaseStruct(FacesContext context, Flow flow, String fromAction, String outcome) {
        CaseStruct result = null;
        
        FlowNode node = flow.getNode(outcome);
        if (null != node) {
            if (node instanceof ViewNode) {
                result = new CaseStruct();
                result.viewId = ((ViewNode)node).getVdlDocumentId();
                result.navCase = new NavigationCase(fromAction, 
                        fromAction, outcome, null, result.viewId, 
                        flow.getDefiningDocumentId(), null, false, false);
            } else if (node instanceof ReturnNode) {
                String fromOutcome = ((ReturnNode)node).getFromOutcome(context);
                FlowHandler flowHandler = context.getApplication().getFlowHandler();
                try {
                    flowHandler.setReturnMode(context, true);
                    result = getViewId(context, fromAction, fromOutcome, FlowHandler.NULL_FLOW);
                    // We are in a return node, but no result can be found from that
                    // node.  Show the last displayed viewId from the preceding flow.
                    if (null == result) {
                        Flow precedingFlow = flowHandler.getCurrentFlow(context);
                        if (null != precedingFlow) {
                            String toViewId = flowHandler.getLastDisplayedViewId(context);
                            if (null != toViewId) {
                                result = new CaseStruct();
                                result.viewId = toViewId;
                                result.navCase = new NavigationCase(context.getViewRoot().getViewId(),
                                        fromAction,
                                        outcome,
                                        null,
                                        toViewId,
                                        FlowHandler.NULL_FLOW,                            
                                        null,
                                        false,
                                        false);
                                
                            }
                        }
                    }
                }
                finally {
                    flowHandler.setReturnMode(context, false);
                }
                
            }
        } else {
            // See if there is an implicit match within this flow, using outcome
            // to derive a view id within this flow.
            String currentViewId = outcome;
            // If the viewIdToTest needs an extension, take one from the currentViewId.
            String currentExtension;
            int idx = currentViewId.lastIndexOf('.');
            if (idx != -1) {
                currentExtension = currentViewId.substring(idx);
            } else {
                // PENDING, don't hard code XHTML here, look it up from configuration
                currentExtension = ".xhtml";
            }
            String viewIdToTest = "/" + flow.getId() + "/" + outcome + currentExtension;
            ViewHandler viewHandler = Util.getViewHandler(context);
            viewIdToTest = viewHandler.deriveViewId(context, viewIdToTest);
            if (null != viewIdToTest) {
                result = new CaseStruct();
                result.viewId = viewIdToTest;
                result.navCase = new NavigationCase(fromAction, 
                        fromAction, outcome, null, result.viewId, 
                        null, false, false);
            }
            
        }
        return result;
    }
    
    private CaseStruct findMethodCallMatch(FacesContext context, String fromAction, String outcome) {
        CaseStruct result = null;
        FlowHandler flowHandler = context.getApplication().getFlowHandler();
        if (null == flowHandler) {
            return null;
        }
        Flow currentFlow = flowHandler.getCurrentFlow(context);
        if (null != currentFlow) {
            FlowNode node = currentFlow.getNode(outcome);
            if (node instanceof MethodCallNode) {
                MethodCallNode methodCallNode = (MethodCallNode) node;
                MethodExpression me = methodCallNode.getMethodExpression();
                if (null != me) {
                    List<Parameter> paramList= methodCallNode.getParameters();
                    Object[] params = null;
                    if (null != paramList) {
                        params = new Object[paramList.size()];
                        int i = 0;
                        ELContext elContext = context.getELContext();
                        for (Parameter cur : paramList) {
                            params[i++] = cur.getValue().getValue(elContext);
                        }
                    }
                    
                    Object invokeResult = me.invoke(context.getELContext(), params);
                    if (null == invokeResult) {
                        ValueExpression ve = methodCallNode.getOutcome();
                        if (null != ve) {
                            invokeResult  = ve.getValue(context.getELContext());
                        }
                    }
                    outcome = invokeResult.toString();
                    result = synthesizeCaseStruct(context, currentFlow, fromAction, outcome);
                    if (null != result) {
                        result.currentFlow = currentFlow;
                        result.newFlow = currentFlow;
                    }
                }
            }
        }
        

        return result;
    }
    
    private CaseStruct findFacesFlowCallMatch(FacesContext context, 
            String fromAction, String outcome, String toFlowDocumentId) {
        CaseStruct result = null;

        FlowHandler flowHandler = context.getApplication().getFlowHandler();
        if (null == flowHandler) {
            return null;
        }
        Flow currentFlow = flowHandler.getCurrentFlow(context);
        Flow newFlow = null;
        FlowCallNode facesFlowCallNode = null;
        if (null != currentFlow) {
            FlowNode node = currentFlow.getNode(outcome);
            if (node instanceof FlowCallNode) {
                facesFlowCallNode = (FlowCallNode) node;
                String flowId = facesFlowCallNode.getCalledFlowId(context);
                String flowDocumentId = facesFlowCallNode.getCalledFlowDocumentId(context);

                if (null != flowId) {
                    newFlow = flowHandler.getFlow(context, flowDocumentId, flowId);
                    if (null != newFlow) {
                        result = synthesizeCaseStruct(context, newFlow, fromAction, flowId);
                    }
                }
            }
        } else {
            // See if we are trying to enter a flow.
            newFlow = flowHandler.getFlow(context, toFlowDocumentId, outcome);
            if (null != newFlow) {
                String startNodeId = newFlow.getStartNodeId();
                result = synthesizeCaseStruct(context, newFlow, fromAction, startNodeId);
                if (null == result) {
                    result = getViewId(context, fromAction, startNodeId, toFlowDocumentId);
                }
            } 
        }
        if (null != result) {
            result.currentFlow = currentFlow;
            result.newFlow = newFlow;
            result.facesFlowCallNode = facesFlowCallNode;
        }
        
        return result;
    }  
    
    private CaseStruct findReturnMatch(FacesContext context, 
            String fromAction, String outcome) {
        CaseStruct result = null;
        FlowHandler flowHandler = context.getApplication().getFlowHandler();
        if (null == flowHandler) {
            return null;
        }
        Flow currentFlow = flowHandler.getCurrentFlow(context);
        if (null != currentFlow) {
            // If so, see if the outcome is one of this flow's 
            // faces-flow-return nodes.
            ReturnNode returnNode = currentFlow.getReturns().get(outcome);
            if (null != returnNode) {
                String fromOutcome = returnNode.getFromOutcome(context);
                try {
                    flowHandler.setReturnMode(context, true);
                    result = getViewId(context, fromAction, fromOutcome, FlowHandler.NULL_FLOW);
                    // We are in a return node, but no result can be found from that
                    // node.  Show the last displayed viewId from the preceding flow.
                    if (null == result) {
                        Flow precedingFlow = flowHandler.getCurrentFlow(context);
                        if (null != precedingFlow) {
                            String toViewId = flowHandler.getLastDisplayedViewId(context);
                            if (null != toViewId) {
                                result = new CaseStruct();
                                result.viewId = toViewId;
                                result.navCase = new NavigationCase(context.getViewRoot().getViewId(),
                                        fromAction,
                                        outcome,
                                        null,
                                        toViewId,
                                        FlowHandler.NULL_FLOW,                            
                                        null,
                                        false,
                                        false);
                                
                            }
                        }
                    }
                }
                finally {
                    flowHandler.setReturnMode(context, false);
                }
            }
        }
        if (null != result) {
            result.currentFlow = currentFlow;
            result.newFlow = null;
        }

        return result;
    }

    
    /**
     * This method will attempt to find the <code>view</code> identifier based on action reference
     * and outcome.  Refer to section 7.4.2 of the specification for more details.
     * @param ctx the {@link FacesContext} for the current request
     * @param caseSet   The list of navigation cases.
     * @param fromAction The action reference string.
     * @param outcome    The outcome string.
     * @return The <code>view</code> identifier.
     */
    private CaseStruct determineViewFromActionOutcome(FacesContext ctx,
                                                      Set<NavigationCase> caseSet,
                                                      String fromAction,
                                                      String outcome,
                                                      String toFlowDocumentId) {

        CaseStruct result = new CaseStruct();
        boolean match = false;
        for (NavigationCase cnc : caseSet) {
            String cncFromAction = cnc.getFromAction();
            String cncFromOutcome = cnc.getFromOutcome();
            boolean cncHasCondition = cnc.hasCondition();
            String cncToViewId = cnc.getToViewId(ctx);
           
            if ((cncFromAction != null) && (cncFromOutcome != null)) {
                if ((cncFromAction.equals(fromAction)) &&
                    (cncFromOutcome.equals(outcome))) {
                    result.viewId = cncToViewId;
                    result.navCase = cnc;
                    match = true;
                }
            } else if ((cncFromAction == null) && (cncFromOutcome != null)) {
                if (cncFromOutcome.equals(outcome)) {
                    result.viewId = cncToViewId;
                    result.navCase = cnc;
                    match = true;
                }
            } else if ((cncFromAction != null) && (cncFromOutcome == null)) {
                if (cncFromAction.equals(fromAction) && (outcome != null || cncHasCondition)) {
                    result.viewId = cncToViewId;
                    result.navCase = cnc;
                    match = true;
                }
            } else if ((cncFromAction == null) && (cncFromOutcome == null)) {
                if (outcome != null || cncHasCondition) {
                    result.viewId = cncToViewId;
                    result.navCase = cnc;
                    match = true;
                }
            }

            if (match) {
                if (cncHasCondition && Boolean.FALSE.equals(cnc.getCondition(ctx))) {
                    match = false;
                } else {
                    toFlowDocumentId = (null != cnc.getToFlowDocumentId()) ? cnc.getToFlowDocumentId() : toFlowDocumentId;
                    if (null != toFlowDocumentId) {
                        FlowHandler fh = ctx.getApplication().getFlowHandler();
                        result.isFlowEntryFromExplicitRule = null != fh.getFlow(ctx, toFlowDocumentId, outcome);
                    }
                    return result;
                }
            }
        }

        return null;
    }


    // ---------------------------------------------------------- Nested Classes


    private static class CaseStruct {
        String viewId;
        NavigationCase navCase;
        Flow currentFlow;
        Flow newFlow;
        FlowCallNode facesFlowCallNode;
        boolean isFlowEntryFromExplicitRule = false;
    }
    
    private static final class NavigationInfo {
        private NavigationMap ruleSet;
        private Map<String, SwitchNode> switches;
    }


    private static final class NavigationMap extends AbstractMap<String,Set<NavigationCase>> {

        private HashMap<String,Set<NavigationCase>> navigationMap =
              new HashMap<String,Set<NavigationCase>>();
        private TreeSet<String> wildcardMatchList =
              new TreeSet<String>(new Comparator<String>() {
                  public int compare(String fromViewId1, String fromViewId2) {
                      return -(fromViewId1.compareTo(fromViewId2));
                  }
              });


        // ---------------------------------------------------- Methods from Map


        @Override
        public int size() {
            return navigationMap.size();
        }


        @Override
        public boolean isEmpty() {
            return navigationMap.isEmpty();
        }

        
        @Override
        public Set<NavigationCase> put(String key, Set<NavigationCase> value) {
            if (key == null) {
                throw new IllegalArgumentException(key);
            }
            if (value == null) {
                throw new IllegalArgumentException();
            }
            updateWildcards(key);
            Set<NavigationCase> existing = navigationMap.get(key);
            if (existing == null) {
                navigationMap.put(key, value);
                return null;
            } else {
                existing.addAll(value);
                return existing;
            }

        }

        @Override
        public void putAll(Map<? extends String, ? extends Set<NavigationCase>> m) {
            if (m == null) {
                return;
            }
            for (Map.Entry<? extends String, ? extends Set<NavigationCase>> entry : m.entrySet()) {
                String key = entry.getKey();
                updateWildcards(key);
                Set<NavigationCase> existing = navigationMap.get(key);
                if (existing == null) {
                    navigationMap.put(key, entry.getValue());
                } else {
                    existing.addAll(entry.getValue());
                }
            }
        }


        @Override
        public Set<String> keySet() {
            return new AbstractSet<String>() {

                @Override
                public Iterator<String> iterator() {
                    return new Iterator<String>() {

                        Iterator<Map.Entry<String,Set<NavigationCase>>> i = entrySet().iterator();

                        @Override
                        public boolean hasNext() {
                            return i.hasNext();
                        }

                        @Override
                        public String next() {
                            return i.next().getKey();
                        }

                        @Override
                        public void remove() {
                            throw new UnsupportedOperationException();
                        }
                    };
                }

                @Override
                public int size() {
                    return NavigationMap.this.size();
                }
            };
        }

        @Override
        public Collection<Set<NavigationCase>> values() {
            return new AbstractCollection<Set<NavigationCase>>() {

                @Override
                public Iterator<Set<NavigationCase>> iterator() {
                    return new Iterator<Set<NavigationCase>>() {

                        Iterator<Map.Entry<String,Set<NavigationCase>>> i = entrySet().iterator();

                        @Override
                        public boolean hasNext() {
                            return i.hasNext();
                        }

                        @Override
                        public Set<NavigationCase> next() {
                            return i.next().getValue();
                        }

                        @Override
                        public void remove() {
                            throw new UnsupportedOperationException();
                        }
                    };
                }

                @Override
                public int size() {
                    return NavigationMap.this.size();
                }
            };
        }

        @Override
        public Set<Entry<String, Set<NavigationCase>>> entrySet() {
            return new AbstractSet<Entry<String, Set<NavigationCase>>>() {

                @Override
                public Iterator<Entry<String, Set<NavigationCase>>> iterator() {

                    return new Iterator<Entry<String,Set<NavigationCase>>>() {

                        Iterator<Entry<String, Set<NavigationCase>>> i =
                              navigationMap.entrySet().iterator();

                        @Override
                        public boolean hasNext() {
                            return i.hasNext();
                        }

                        @Override
                        public Entry<String, Set<NavigationCase>> next() {
                            return i.next();
                        }

                        @Override
                        public void remove() {
                            throw new UnsupportedOperationException();
                        }
                    };
                }

                @Override
                public int size() {
                    return NavigationMap.this.size();
                }
            };
        }


        // ----------------------------------------------------- Private Methods

        private void updateWildcards(String fromViewId) {

            if (!navigationMap.containsKey(fromViewId)) {
                if (fromViewId.endsWith("*")) {
                    wildcardMatchList.add(fromViewId.substring(0, fromViewId.lastIndexOf('*')));
                }
            }
            
        }

    }
}
