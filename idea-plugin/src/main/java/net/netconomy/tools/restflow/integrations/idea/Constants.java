package net.netconomy.tools.restflow.integrations.idea;

import javax.swing.Icon;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.extensions.PluginId;


public class Constants {

    public static final PluginId PLUGIN_ID = PluginId.getId("net.netconomy.tools.restflow.integrations.idea");

    public static final Icon RUN_ICON = AllIcons.RunConfigurations.TestState.Run; // gray: AllIcons.Toolwindows.ToolWindowRun

    public static final String ERRORS_NOTIFICATION_GROUP = "restflow.errors";

    public static final String RESTFLOW_FILE_EXTENSION = ".restflow";

    public static final String RESTFLOW_CLASS_NAME = "net.netconomy.tools.restflow.dsl.RestFlow";
    public static final String RESTFLOW_SCRIPT_CLASS_NAME = "net.netconomy.tools.restflow.impl.RestFlowScript";

    public static final String RESTFLOW_RUN_INTENTION_FAMILY = "RESTflowRun";

    public static final String ID_BASE = "net.netconomy.tools.restflow.integrations.idea";

    public static final String RESTFLOW_JARS_BASE = "net/netconomy/tools/restflow/jars/";
    public static final String RESTFLOW_CONSOLE_JAR_NAME = "console.jar";
    public static final String RESTFLOW_CONSOLE_JAR = RESTFLOW_JARS_BASE + "/" + RESTFLOW_CONSOLE_JAR_NAME;
    public static final String RESTFLOW_LIB_BASE = RESTFLOW_JARS_BASE + "lib/";
    public static final String RESTFLOW_LIB_INDEX_NAME = "index.txt";
    public static final String RESTFLOW_LIB_INDEX = RESTFLOW_LIB_BASE + RESTFLOW_LIB_INDEX_NAME;
    public static final String RESTFLOW_GLOBAL_LIBRARY = "RESTflow";

    public static final String RESTFLOW_NOTIFICATION_GROUP = "ch.raffael.restflow";

    private Constants() {
    }

    public static String id(String subId) {
        return ID_BASE + "." + subId;
    }

}
