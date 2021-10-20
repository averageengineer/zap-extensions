/*
 * Zed Attack Proxy (ZAP) and its related class files.
 *
 * ZAP is an HTTP/HTTPS proxy for assessing web application security.
 *
 * Copyright 2021 The ZAP Development Team
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.zaproxy.addon.automation.jobs;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import org.apache.commons.lang3.EnumUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.parosproxy.paros.Constant;
import org.parosproxy.paros.core.scanner.Alert;
import org.parosproxy.paros.core.scanner.Plugin.AlertThreshold;
import org.parosproxy.paros.core.scanner.Plugin.AttackStrength;
import org.zaproxy.addon.automation.AutomationEnvironment;
import org.zaproxy.addon.automation.AutomationJob;
import org.zaproxy.addon.automation.AutomationProgress;

public class JobUtils {

    private static final String THRESHOLD_I18N_PREFIX = "ascan.options.level.";
    private static final String STRENGTH_I18N_PREFIX = "ascan.options.strength.";

    private static Map<String, String> strengthI18nToStr;
    private static Map<String, String> thresholdI18nToStr;

    private static final Logger LOG = LogManager.getLogger(JobUtils.class);

    static {
        strengthI18nToStr = new HashMap<>();
        for (AttackStrength at : AttackStrength.values()) {
            strengthI18nToStr.put(
                    JobUtils.strengthToI18n(at.name()), at.name().toLowerCase(Locale.ROOT));
        }
        thresholdI18nToStr = new HashMap<>();
        for (AlertThreshold at : AlertThreshold.values()) {
            thresholdI18nToStr.put(
                    JobUtils.thresholdToI18n(at.name()), at.name().toLowerCase(Locale.ROOT));
        }
    }

    public static AttackStrength parseAttackStrength(
            Object o, String jobName, AutomationProgress progress) {
        AttackStrength strength = null;
        if (o == null) {
            return null;
        }
        if (o instanceof String) {
            try {
                strength = AttackStrength.valueOf(((String) o).toUpperCase());
            } catch (Exception e) {
                progress.warn(
                        Constant.messages.getString("automation.error.ascan.strength", jobName, o));
            }
        } else {
            progress.warn(
                    Constant.messages.getString("automation.error.ascan.strength", jobName, o));
        }
        return strength;
    }

    public static AlertThreshold parseAlertThreshold(
            Object o, String jobName, AutomationProgress progress) {
        AlertThreshold threshold = null;
        if (o == null) {
            return null;
        }
        if (o instanceof String) {
            try {
                threshold = AlertThreshold.valueOf(((String) o).toUpperCase());
            } catch (Exception e) {
                progress.warn(
                        Constant.messages.getString(
                                "automation.error.ascan.threshold", jobName, o));
            }
        } else if (o instanceof Boolean && (!(Boolean) o)) {
            // This will happen if OFF is not quoted
            threshold = AlertThreshold.OFF;
        } else {
            progress.warn(
                    Constant.messages.getString("automation.error.ascan.threshold", jobName, o));
        }
        return threshold;
    }

    public static Integer parseAlertConfidence(Object o) {
        return parseAlertConfidence(o, null, null);
    }

    public static Integer parseAlertConfidence(
            Object o, String jobName, AutomationProgress progress) {
        if (o == null) {
            return null;
        }
        String str = o.toString().trim().toLowerCase();
        if (str.isEmpty()) {
            return null;
        }
        for (int i = 0; i < Alert.MSG_CONFIDENCE.length; i++) {
            if (Alert.MSG_CONFIDENCE[i].toLowerCase().equals(str)) {
                return i;
            }
        }
        if (progress != null) {
            progress.warn(
                    Constant.messages.getString("automation.error.badconfidence", jobName, o));
        }
        return null;
    }

    public static Integer parseAlertRisk(Object o) {
        return parseAlertRisk(o, null, null);
    }

    public static Integer parseAlertRisk(Object o, String jobName, AutomationProgress progress) {
        if (o == null) {
            return null;
        }
        String str = o.toString().trim().toLowerCase();
        if (str.isEmpty()) {
            return null;
        }
        for (int i = 0; i < Alert.MSG_RISK.length; i++) {
            if (Alert.MSG_RISK[i].toLowerCase().equals(str)) {
                return i;
            }
        }
        if (progress != null) {
            progress.warn(Constant.messages.getString("automation.error.badrisk", jobName, o));
        }
        return null;
    }

    public static String strengthToI18n(String str) {
        if (str == null) {
            return "";
        }
        return Constant.messages.getString(STRENGTH_I18N_PREFIX + str.toLowerCase(Locale.ROOT));
    }

    public static String i18nToStrength(String str) {
        return strengthI18nToStr.get(str);
    }

    public static String thresholdToI18n(String str) {
        if (str == null) {
            return "";
        }
        return Constant.messages.getString(THRESHOLD_I18N_PREFIX + str.toLowerCase(Locale.ROOT));
    }

    public static String i18nToThreshold(String str) {
        return thresholdI18nToStr.get(str);
    }

    public static Object getJobOptions(AutomationJob job, AutomationProgress progress) {
        Object obj = job.getParamMethodObject();
        String optionsGetterName = job.getParamMethodName();
        if (obj != null && optionsGetterName != null) {
            try {
                Method method = obj.getClass().getDeclaredMethod(optionsGetterName);
                method.setAccessible(true);
                return method.invoke(obj);
            } catch (Exception e1) {
                progress.error(
                        Constant.messages.getString(
                                "automation.error.options.method",
                                obj.getClass().getCanonicalName(),
                                optionsGetterName,
                                e1.getMessage()));
                return null;
            }
        }
        return null;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void applyParamsToObject(
            Map<?, ?> testData,
            Object object,
            String objectName,
            String[] ignore,
            AutomationProgress progress) {
        if (testData == null || object == null) {
            return;
        }
        Map<String, Method> methodMap = null;
        List<String> ignoreList = Collections.emptyList();
        if (ignore != null) {
            ignoreList = Arrays.asList(ignore);
        }

        try {
            Method[] methods = object.getClass().getMethods();
            methodMap = new HashMap<>(methods.length);
            for (Method m : methods) {
                methodMap.put(m.getName(), m);
            }
        } catch (Exception e1) {
            LOG.error(e1.getMessage(), e1);
            progress.error(
                    Constant.messages.getString(
                            "automation.error.options.methods", objectName, e1.getMessage()));
            return;
        }

        for (Entry<?, ?> param : testData.entrySet()) {
            String key = param.getKey().toString();
            if (param.getValue() == null) {
                continue;
            }
            if (ignoreList.contains(key)) {
                continue;
            }

            String paramMethodName = "set" + key.toUpperCase().charAt(0) + key.substring(1);
            Method optMethod = methodMap.get(paramMethodName);
            if (optMethod != null) {
                if (optMethod.getParameterCount() > 0) {
                    Object value = null;
                    Class<?> paramType = optMethod.getParameterTypes()[0];
                    try {
                        value = objectToType(param.getValue(), paramType);
                    } catch (NumberFormatException e1) {
                        progress.error(
                                Constant.messages.getString(
                                        "automation.error.options.badint",
                                        objectName,
                                        key,
                                        param.getValue()));
                        continue;
                    } catch (IllegalArgumentException e1) {
                        if (Enum.class.isAssignableFrom(paramType)) {
                            progress.error(
                                    Constant.messages.getString(
                                            "automation.error.options.badenum",
                                            objectName,
                                            key,
                                            EnumUtils.getEnumList((Class<Enum>) paramType)));
                        } else {
                            progress.error(
                                    Constant.messages.getString(
                                            "automation.error.options.badbool",
                                            objectName,
                                            key,
                                            param.getValue()));
                        }
                        continue;
                    }
                    if (value != null) {
                        try {
                            optMethod.invoke(object, value);
                            progress.info(
                                    Constant.messages.getString(
                                            "automation.info.setparam",
                                            objectName, // TODO changed param
                                            key,
                                            value));
                        } catch (Exception e) {
                            progress.error(
                                    Constant.messages.getString(
                                            "automation.error.options.badcall",
                                            objectName,
                                            paramMethodName,
                                            e.getMessage()));
                        }
                    } else {
                        progress.error(
                                Constant.messages.getString(
                                        "automation.error.options.badtype",
                                        objectName, // TODO changed param
                                        paramMethodName,
                                        optMethod.getParameterTypes()[0].getCanonicalName()));
                    }
                }
            } else {
                // This is likely to be caused by the user using an invalid name, rather than a
                // coding issue
                progress.warn(
                        Constant.messages.getString(
                                "automation.error.options.unknown", objectName, key));
            }
        }
    }

    public static void applyObjectToObject(
            Object srcObject,
            Object destObject,
            String objectName,
            String[] ignore,
            AutomationProgress progress,
            AutomationEnvironment env) {
        if (srcObject == null || destObject == null) {
            return;
        }
        List<String> ignoreList = Collections.emptyList();
        if (ignore != null) {
            ignoreList =
                    Arrays.asList(ignore).stream()
                            .map(e -> "get" + e.toUpperCase().charAt(0) + e.substring(1))
                            .collect(Collectors.toList());
        }

        try {
            Method[] methods = srcObject.getClass().getMethods();
            for (Method m : methods) {
                String getterName = m.getName();
                if (getterName.startsWith("get")
                        && m.getParameterCount() == 0
                        && !getterName.equals("getClass")
                        && !ignoreList.contains(getterName)) {
                    // Its a getter so process it
                    String setterName = "s" + getterName.substring(1);
                    try {
                        Object value = m.invoke(srcObject);
                        if (value == null) {
                            continue;
                        }

                        Method setterMethod = null;
                        try {
                            setterMethod =
                                    destObject.getClass().getMethod(setterName, m.getReturnType());
                        } catch (Exception e) {
                            // Ignore
                        }
                        if (setterMethod == null) {
                            Class<?> c = toBaseClass(m.getReturnType());
                            if (c != null) {
                                try {
                                    setterMethod = destObject.getClass().getMethod(setterName, c);
                                } catch (Exception e) {
                                    // Ignore
                                }
                            }
                        }
                        if (setterMethod != null) {
                            if (value instanceof String) {
                                // Can only really replace envvars in Strings otherwise it will
                                // break GUI controls
                                value = env.replaceVars(value);
                            }
                            setterMethod.invoke(destObject, value);
                        } else {
                            LOG.error(
                                    "Automation Framework failed to find method {} on {}",
                                    setterName,
                                    destObject.getClass().getCanonicalName());
                        }

                    } catch (Exception e) {
                        LOG.error(e.getMessage(), e);
                    }
                }
            }

        } catch (Exception e1) {
            LOG.error(e1.getMessage(), e1);
            progress.error(
                    Constant.messages.getString(
                            "automation.error.options.methods", objectName, e1.getMessage()));
            return;
        }
    }

    private static Class<?> toBaseClass(Class<?> origClass) {
        if (origClass.equals(Integer.class)) {
            return int.class;
        }
        if (origClass.equals(Long.class)) {
            return long.class;
        }
        if (origClass.equals(Boolean.class)) {
            return boolean.class;
        }
        return null;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    static <T> T objectToType(Object obj, T t) {
        if (String.class.equals(t)) {
            return (T) obj.toString();
        } else if (Integer.class.equals(t) || int.class.equals(t)) {
            return (T) (Object) Integer.parseInt(obj.toString());
        } else if (Long.class.equals(t) || long.class.equals(t)) {
            return (T) (Object) Long.parseLong(obj.toString());
        } else if (Boolean.class.equals(t) || boolean.class.equals(t)) {
            // Don't use Boolean.parseBoolean as it won't reject illegal values
            String s = obj.toString().trim().toLowerCase();
            if ("true".equals(s)) {
                return (T) Boolean.TRUE;
            } else if ("false".equals(s)) {
                return (T) Boolean.FALSE;
            }
            throw new IllegalArgumentException("Invalid boolean value: " + obj.toString());
        } else if (Map.class.equals(t)) {
            if (obj instanceof Map) {
                HashMap map = new HashMap<>();
                map.putAll((Map) obj);
                return (T) map;
            } else {
                LOG.error("Unable to map to a Map from {}", obj.getClass().getCanonicalName());
            }
        } else if (Enum.class.isAssignableFrom((Class<T>) t)) {
            T enumType = (T) EnumUtils.getEnumIgnoreCase((Class<Enum>) t, obj.toString());
            if (enumType != null) {
                return enumType;
            }
            throw new IllegalArgumentException(
                    "Enum value must be one of " + EnumUtils.getEnumList((Class<Enum>) t));
        }

        return null;
    }

    public static int unBox(Integer i) {
        if (i == null) {
            return 0;
        }
        return i;
    }

    public static long unBox(Long i) {
        if (i == null) {
            return 0;
        }
        return i;
    }

    public static boolean unBox(Boolean i) {
        if (i == null) {
            return false;
        }
        return i;
    }

    public static String unBox(String s, String defaultStr) {
        if (s == null) {
            return defaultStr;
        }
        return s;
    }
}
