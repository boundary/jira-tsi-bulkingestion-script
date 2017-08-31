package com.bmc.truesight.saas.jira.util;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bmc.truesight.saas.jira.beans.Template;
import com.bmc.truesight.saas.jira.exception.JiraApiInstantiationFailedException;
import com.bmc.truesight.saas.jira.exception.ParsingException;
import com.bmc.truesight.saas.jira.exception.ValidationException;
import com.bmc.truesight.saas.jira.impl.GenericTemplateParser;
import com.bmc.truesight.saas.jira.impl.GenericTemplatePreParser;
import com.bmc.truesight.saas.jira.impl.GenericTemplateValidator;
import com.bmc.truesight.saas.jira.in.TemplateParser;
import com.bmc.truesight.saas.jira.in.TemplatePreParser;
import com.bmc.truesight.saas.jira.in.TemplateValidator;

public class ScriptUtil {

    private final static Logger log = LoggerFactory.getLogger(ScriptUtil.class);

    public static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss z");

    public static String dateToString(Date date) {
        DATE_FORMAT.setTimeZone(TimeZone.getTimeZone("UTC"));
        return DATE_FORMAT.format(date);
    }

    public static Template prepareTemplate() throws ParsingException, ValidationException, IOException, JiraApiInstantiationFailedException {
        String path = null;

        path = new java.io.File(".").getCanonicalPath();
        path += Constants.TEMPLATE_PATH;

        // PARSING THE CONFIGURATION FILE
        Template template = null;
        TemplatePreParser preParser = new GenericTemplatePreParser();
        TemplateParser parser = new GenericTemplateParser();
        Template defaultTemplate = preParser.loadDefaults();
        log.debug("Template defaults loading sucessfuly finished ");
        template = parser.readParseConfigFile(defaultTemplate, path);
        template = parser.ignoreFields(template);
        log.debug("User template configuration parsing successful ");

        // VALIDATION OF THE CONFIGURATION
        TemplateValidator validator = new GenericTemplateValidator();
        validator.validate(template);

        return template;
    }

}
