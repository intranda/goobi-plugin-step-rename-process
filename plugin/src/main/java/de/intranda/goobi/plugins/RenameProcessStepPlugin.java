package de.intranda.goobi.plugins;

import java.io.IOException;

/**
 * This file is part of a plugin for Goobi - a Workflow tool for the support of mass digitization.
 *
 * Visit the websites for more information.
 *          - https://goobi.io
 *          - https://www.intranda.com
 *          - https://github.com/intranda/goobi
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 2 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program; if not, write to the Free Software Foundation, Inc., 59
 * Temple Place, Suite 330, Boston, MA 02111-1307 USA
 *
 */

import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.configuration.SubnodeConfiguration;
import org.apache.commons.lang3.StringUtils;
import org.goobi.beans.Step;
import org.goobi.production.enums.LogType;
import org.goobi.production.enums.PluginGuiType;
import org.goobi.production.enums.PluginReturnValue;
import org.goobi.production.enums.PluginType;
import org.goobi.production.enums.StepReturnValue;
import org.goobi.production.plugin.interfaces.IStepPluginVersion2;

import de.sub.goobi.config.ConfigPlugins;
import de.sub.goobi.helper.Helper;
import de.sub.goobi.helper.VariableReplacer;
import de.sub.goobi.helper.exceptions.SwapException;
import de.sub.goobi.persistence.managers.ProcessManager;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import net.xeoh.plugins.base.annotations.PluginImplementation;
import ugh.dl.DigitalDocument;
import ugh.dl.Prefs;
import ugh.exceptions.PreferencesException;
import ugh.exceptions.ReadException;

@PluginImplementation
@Log4j2
public class RenameProcessStepPlugin implements IStepPluginVersion2 {
    
    @Getter
    private String title = "intranda_step_rename_process";
    @Getter
    private Step step;

    private String returnPath;

    private String newProcessTitle; // title used to replace the old one
    private transient VariableReplacer replacer;
    private org.goobi.beans.Process process;
    private static final String PATTERN_REGEX = "\\{(.*?)\\}"; // regex used for matching unrecognized goobi variables
    private Pattern pattern = Pattern.compile(PATTERN_REGEX);

    @Override
    public void initialize(Step step, String returnPath) {
        log.debug("=============================== Starting Rename Process ===============================");

        this.returnPath = returnPath;
        this.step = step;
                
        // read parameters from correct block in configuration file
        SubnodeConfiguration myConfig = ConfigPlugins.getProjectAndStepConfig(title, step);

        newProcessTitle = myConfig.getString("newProcessTitle", "");
        log.debug("processTitle = " + newProcessTitle);

        // initialize VariableReplacer
        process = step.getProzess();
        try {
            DigitalDocument dd = process.readMetadataFile().getDigitalDocument();
            Prefs prefs = process.getRegelsatz().getPreferences();
            replacer = new VariableReplacer(dd, prefs, process, step);
        } catch (ReadException | IOException | SwapException | PreferencesException e) {
            logBoth(process.getId(), LogType.ERROR, "Exception happened during initialization: " + e.getMessage());
        }

        // replace goobi variables
        newProcessTitle = replacer.replace(newProcessTitle);
        log.debug("newProcessTitle = " + newProcessTitle);

        // remove all spaces in between and from both ends
        newProcessTitle = newProcessTitle.replace(" ", "").strip();
        log.debug("newProcessTitle = " + newProcessTitle);

        logBoth(process.getId(), LogType.INFO, "RenameProcess step plugin initialized");
    }

    @Override
    public PluginGuiType getPluginGuiType() {
        return PluginGuiType.NONE;
    }

    @Override
    public String getPagePath() {
        return "/uii/plugin_step_rename_process.xhtml";
    }

    @Override
    public PluginType getType() {
        return PluginType.Step;
    }

    @Override
    public String cancel() {
        return "/uii" + returnPath;
    }

    @Override
    public String finish() {
        return "/uii" + returnPath;
    }
    
    @Override
    public int getInterfaceVersion() {
        return 0;
    }

    @Override
    public HashMap<String, StepReturnValue> validate() {
        return null;
    }
    
    @Override
    public boolean execute() {
        PluginReturnValue ret = run();
        return ret != PluginReturnValue.ERROR;
    }

    @Override
    public PluginReturnValue run() {
        log.debug("process title before = " + process.getTitel());
        
        // validation of newProcessTitle
        if (newProcessTitle.contains("{")) {
            // there exists some unrecognized goobi variable
            // find them out and place a warning
            Matcher matcher = pattern.matcher(newProcessTitle);
            while(matcher.find()) {
                logBoth(process.getId(), LogType.WARN, "Unrecognized Goobi Variable: " + matcher.group(1));
            }
            newProcessTitle = newProcessTitle.replaceAll(PATTERN_REGEX, "");
        }
        if (StringUtils.isBlank(newProcessTitle)) {
            // do not rename the process
            logBoth(process.getId(), LogType.ERROR, "Cannot rename a process with blank!");
            return PluginReturnValue.ERROR;
        }

        process.changeProcessTitle(newProcessTitle);
        ProcessManager.saveProcessInformation(process);

        log.debug("process title after = " + process.getTitel());

        logBoth(process.getId(), LogType.INFO, "RenameProcess step plugin executed");
        log.debug("=============================== Stopping Rename Process ===============================");

        return PluginReturnValue.FINISH;
    }

    /**
     * 
     * @param processId
     * @param logType
     * @param message message to be shown to both terminal and journal
     */
    private void logBoth(int processId, LogType logType, String message) {
        String logMessage = "Rename Process Step Plugin: " + message;
        switch (logType) {
            case ERROR:
                log.error(logMessage);
                break;
            case DEBUG:
                log.debug(logMessage);
                break;
            case WARN:
                log.warn(logMessage);
                break;
            default: // INFO
                log.info(logMessage);
                break;
        }
        if (processId > 0) {
            Helper.addMessageToProcessJournal(processId, logType, logMessage);
        }
    }
}
