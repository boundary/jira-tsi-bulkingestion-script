package com.bmc.truesight.saas.jira;

import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bmc.truesight.saas.jira.beans.Configuration;
import com.bmc.truesight.saas.jira.beans.Error;
import com.bmc.truesight.saas.jira.beans.JiraEventResponse;
import com.bmc.truesight.saas.jira.beans.Result;
import com.bmc.truesight.saas.jira.beans.Success;
import com.bmc.truesight.saas.jira.beans.TSIEvent;
import com.bmc.truesight.saas.jira.beans.Template;
import com.bmc.truesight.saas.jira.exception.BulkEventsIngestionFailedException;
import com.bmc.truesight.saas.jira.exception.JiraApiInstantiationFailedException;
import com.bmc.truesight.saas.jira.exception.JiraLoginFailedException;
import com.bmc.truesight.saas.jira.exception.JiraReadFailedException;
import com.bmc.truesight.saas.jira.exception.ParsingException;
import com.bmc.truesight.saas.jira.exception.ValidationException;
import com.bmc.truesight.saas.jira.impl.EventIngestionExecuterService;
import com.bmc.truesight.saas.jira.impl.JiraReader;
import com.bmc.truesight.saas.jira.integration.adapter.JiraEntryEventAdapter;
import com.bmc.truesight.saas.jira.util.Constants;
import com.bmc.truesight.saas.jira.util.ScriptUtil;
import com.opencsv.CSVWriter;

/**
 * Jira bulk Ingestion Script
 *
 * @author vitiwari
 */
public class JiraScriptApp {

    private final static Logger log = LoggerFactory.getLogger(JiraScriptApp.class);
    private static boolean exportToCsvFlag = false;
    private static int availableRecordsSize = 0;

    public static void main(String[] args) {
        if (args.length > 0) {
            String logLevel = args[0];
            setLoglevel(logLevel);
        }
        Template template = inputChoice();
        if (template != null) {
            readAndIngest(template);
        }

    }

    private static Template inputChoice() {
        boolean isTemplateValid = false;
        boolean ingestionFlag = false;

        Template template = null;
        try {
            template = ScriptUtil.prepareTemplate();
            isTemplateValid = true;
        } catch (ParsingException e) {
            log.error(e.getMessage());
        } catch (ValidationException e) {
            log.error(e.getMessage());
        } catch (IOException e) {
            log.error("The Incident template file could not be found, please check the file name and location");
        } catch (Exception e) {
            log.error(e.getMessage());
        }
        log.debug("Template file reading and parsing successful status = {}", isTemplateValid);
        if (isTemplateValid) {
            JiraReader jiraReader = null;
            boolean hasLoggedIntoJira = false;
            try {
                jiraReader = new JiraReader(template);
                hasLoggedIntoJira = jiraReader.validateCredentials();
            } catch (JiraLoginFailedException e) {
                log.error(e.getMessage());
                template = null;
            } catch (JiraApiInstantiationFailedException e) {
                log.error(e.getMessage());
                template = null;
            }
            if (hasLoggedIntoJira) {
                try {
                    availableRecordsSize = jiraReader.getAvailableRecordsCount();
                    System.out.println(availableRecordsSize + " Tickets available for the input time range, do you want to ingest these to TSIntelligence?(y/n)");
                    Scanner scanner = new Scanner(System.in);
                    String input = scanner.next();
                    if (input.equalsIgnoreCase("y")) {
                        ingestionFlag = true;
                    }
                } catch (JiraReadFailedException e) {
                    log.error(e.getMessage());
                } catch (ParseException e) {
                    log.error(e.getMessage());
                } catch (JiraApiInstantiationFailedException e) {
                    log.error(e.getMessage());
                } finally {
                    //jiraReader.logout(user);
                }
                if (ingestionFlag) {
                    System.out.println("Do you also want to export these events as CSV?(y/n)");
                    Scanner scanner = new Scanner(System.in);
                    String input1 = scanner.next();
                    if (input1.equalsIgnoreCase("y")) {
                        exportToCsvFlag = true;
                    }
                } else {
                    template = null;
                }
            }
        }
        return template;
    }

    private static void setLoglevel(String module) {
        switch (module) {
            case "ALL":
            case "all":
                LogManager.getLogger("com.bmc.truesight").setLevel(Level.ALL);
            case "DEBUG":
            case "debug":
                LogManager.getLogger("com.bmc.truesight").setLevel(Level.DEBUG);
                break;
            case "ERROR":
            case "error":
                LogManager.getLogger("com.bmc.truesight").setLevel(Level.ERROR);
                break;
            case "FATAL":
            case "fatal":
                LogManager.getLogger("com.bmc.truesight").setLevel(Level.FATAL);
                break;
            case "INFO":
            case "info":
                LogManager.getLogger("com.bmc.truesight").setLevel(Level.INFO);
                break;
            case "OFF":
            case "off":
                LogManager.getLogger("com.bmc.truesight").setLevel(Level.OFF);
                break;
            case "TRACE":
            case "trace":
                LogManager.getLogger("com.bmc.truesight").setLevel(Level.TRACE);
                break;
            case "WARN":
            case "warn":
                LogManager.getLogger("com.bmc.truesight").setLevel(Level.WARN);
                break;
            default:
                log.error("Argument not recognised, available argument is <loglevel>(ex DEBUG).");
                System.exit(0);
        }
    }

    private static void readAndIngest(Template template) {

        boolean hasLoggedIntoJira = false;
        CSVWriter writer = null;
        Configuration config = template.getConfig();
        JiraEventResponse jiraResponse = new JiraEventResponse();
        List<TSIEvent> lastEventList = new ArrayList<>();
        try {
            JiraReader reader = new JiraReader(template);
            hasLoggedIntoJira = reader.validateCredentials();
            JiraEntryEventAdapter adapter = new JiraEntryEventAdapter();
            int chunkSize = config.getChunkSize();
            int startFrom = 0;
            int iteration = 1;
            int totalRecordsRead = 0;
            boolean readNext = true;
            int totalFailure = 0;
            int totalSuccessful = 0;
            int validRecords = 0;
            log.info("Started reading {} Jira issues starting from index {} , [Start Date: {}, End Date: {}]", new Object[]{chunkSize, startFrom, ScriptUtil.dateToString(config.getStartDateTime()), ScriptUtil.dateToString(config.getEndDateTime())});
            Map<String, List<String>> errorsMap = new HashMap<>();
            List<String> droppedEventIds = new ArrayList<>();
            //Reading first Iteration to get the Idea of total available count
            String csv = "Jira_records_" + config.getStartDateTime().getTime() + "_TO_" + config.getEndDateTime().getTime() + ".csv";
            String[] headers = getFieldHeaders(template);
            if (exportToCsvFlag) {
                try {
                    writer = new CSVWriter(new FileWriter(csv));
                } catch (IOException e) {
                    log.error("CSV file creation failed[{}], Do you want to proceed without csv export ?(y/n)", e.getMessage());
                    Scanner scanner = new Scanner(System.in);
                    String input = scanner.next();
                    if (input.equalsIgnoreCase("n")) {
                        System.exit(0);
                    } else {
                        resetExportToCSVFlag();
                    }
                }
                writer.writeNext(headers);
            }

            while (readNext) {
                log.info("_________________Iteration : " + iteration);
                jiraResponse = reader.readJiraTickets(startFrom, chunkSize, adapter);

                int recordsCount = jiraResponse.getValidEventList().size() + jiraResponse.getInvalidEventIdsList().size();
                totalRecordsRead += recordsCount;
                validRecords += jiraResponse.getValidEventList().size();
                if (recordsCount < chunkSize && totalRecordsRead < availableRecordsSize) {
                    log.info(" Request Sent to Jira (startFrom:" + startFrom + ",chunkSize:" + chunkSize + "), Response(Valid Event(s):" + jiraResponse.getValidEventList().size() + ", Invalid Event(s):" + jiraResponse.getInvalidEventIdsList().size() + ", totalRecordsRead: (" + totalRecordsRead + "/" + availableRecordsSize + ")");
                    log.info(" Based on response as, adjusting the chunk Size as " + recordsCount);
                    chunkSize = recordsCount;
                } else if (recordsCount <= chunkSize) {
                    log.info(" Request Sent to Jira (startFrom:" + startFrom + ",chunkSize:" + chunkSize + "), Response(Valid Event(s):" + jiraResponse.getValidEventList().size() + ", Invalid Event(s):" + jiraResponse.getInvalidEventIdsList().size() + ", totalRecordsRead: (" + totalRecordsRead + "/" + availableRecordsSize + ")");
                }
                if (totalRecordsRead < availableRecordsSize && (totalRecordsRead + chunkSize) > availableRecordsSize) {
                    //assuming the long value would be in int range always
                    chunkSize = ((int) (availableRecordsSize) - totalRecordsRead);
                } else if (totalRecordsRead >= availableRecordsSize) {
                    readNext = false;
                }
                iteration++;
                startFrom = totalRecordsRead;
                if (jiraResponse.getInvalidEventList().size() > 0) {
                    List<String> eventIds = new ArrayList<>();
                    for (TSIEvent event : jiraResponse.getInvalidEventList()) {
                        eventIds.add(event.getProperties().get(Constants.FIELD_FETCH_KEY));
                    }
                    droppedEventIds.addAll(eventIds);
                    log.error("following Jira issue ids are larger than allowed limits [{}]", String.join(",", eventIds));
                }
                if (exportToCsvFlag) {
                    for (TSIEvent event : jiraResponse.getValidEventList()) {
                        writer.writeNext(getFieldValues(event, headers));
                    }
                    log.debug("{} events written to the CSV file", jiraResponse.getValidEventList().size());
                }
                Result result = new EventIngestionExecuterService().ingestEvents(jiraResponse.getValidEventList(), config);
                if (result != null && result.getAccepted() != null) {
                    totalSuccessful += result.getAccepted().size();
                }
                if (result != null && result.getErrors() != null) {
                    totalFailure += result.getErrors().size();
                }
                lastEventList = new ArrayList<>(jiraResponse.getValidEventList());
                if (result != null && result.getSuccess() == Success.PARTIAL) {
                    for (Error error : result.getErrors()) {
                        String id = "";
                        String msg = error.getMessage().trim();
                        id = jiraResponse.getValidEventList().get(error.getIndex()).getProperties().get(Constants.FIELD_FETCH_KEY);
                        if (errorsMap.containsKey(msg)) {
                            List<String> errorsId = errorsMap.get(msg);
                            errorsId.add(id);
                            errorsMap.put(msg, errorsId);
                        } else {
                            List<String> errorsId = new ArrayList<String>();
                            errorsId.add(id);
                            errorsMap.put(msg, errorsId);
                        }
                    }
                }

            }
            log.info("__________________ Jira Issues ingestion to truesight intelligence final status: Jira Record(s) = {}, Valid Record(s) Sent = {}, Successful = {} , Failure = {} ______", new Object[]{availableRecordsSize, validRecords, totalSuccessful, totalFailure});
            if (exportToCsvFlag) {
                log.info("__________________{} event(s) written to the CSV file {}", validRecords, csv);
            }
            if (droppedEventIds.size() > 0) {
                log.error("______Following {} events were invalid & dropped. {}", droppedEventIds.size(), droppedEventIds);
            }
            if (totalFailure > 0) {
                log.error("__________________ Failures (No of times seen), [Reference Id(s)] ______");
                errorsMap.keySet().forEach(msg -> {
                    log.error(msg + " (" + errorsMap.get(msg).size() + "), " + errorsMap.get(msg));
                });
            }
        } catch (JiraLoginFailedException e) {
            log.error("Login Failed : {}", e.getMessage());
        } catch (JiraReadFailedException e) {
            if (lastEventList.isEmpty()) {
                log.error("Reading tickets from Jira server failed for StartDateTime, EndDateTime ({},{}). Please try running the script after some time with the same timestamps.", new Object[]{e.getMessage(), ScriptUtil.dateToString(config.getStartDateTime()), ScriptUtil.dateToString(config.getEndDateTime())});
            } else {
                log.error("Due to some issue Reading tickets from Jira server failed for StartDateTime, EndDateTime ({},{}). Please try running the script after some time from the last successful timestamp as below.", new Object[]{ScriptUtil.dateToString(config.getStartDateTime()), ScriptUtil.dateToString(config.getEndDateTime())});
                Date createdDate = convertToDate(lastEventList.get((lastEventList.size() - 1)).getCreatedAt());
                Date modDate = convertToDate(lastEventList.get((lastEventList.size() - 1)).getProperties().get("LastModDate"));
                Date closedDate = convertToDate(lastEventList.get((lastEventList.size() - 1)).getProperties().get("ClosedDate"));
                log.info("Created Date : {}", new Object[]{createdDate});
                if (lastEventList.get((lastEventList.size() - 1)).getProperties().get("LastModDate") != null) {
                    log.info("Last Modified Date : {}", new Object[]{modDate});
                }
                if (lastEventList.get((lastEventList.size() - 1)).getProperties().get("ClosedDate") != null) {
                    log.info("Closed Date : {}", new Object[]{closedDate});
                }
            }
        } catch (BulkEventsIngestionFailedException e) {
            if (lastEventList.isEmpty()) {
                log.error("Ingestion Failed (Reason : {}) for StartDateTime, EndDateTime ({},{}). Please try running the script after some time with the same timestamps.", new Object[]{e.getMessage(), ScriptUtil.dateToString(config.getStartDateTime()), ScriptUtil.dateToString(config.getEndDateTime())});
            } else {
                log.error("Ingestion Failed (Reason : {}) for StartDateTime, EndDateTime ({},{}). Please try running the script after some time from the last successful timestamp as below.", new Object[]{e.getMessage(), ScriptUtil.dateToString(config.getStartDateTime()), ScriptUtil.dateToString(config.getEndDateTime())});
                Date createdDate = convertToDate(lastEventList.get((lastEventList.size() - 1)).getCreatedAt());
                Date modDate = convertToDate(lastEventList.get((lastEventList.size() - 1)).getProperties().get("LastModDate"));
                Date closedDate = convertToDate(lastEventList.get((lastEventList.size() - 1)).getProperties().get("ClosedDate"));
                log.info("Created Date : {}", new Object[]{ScriptUtil.dateToString(createdDate)});
                if (lastEventList.get((lastEventList.size() - 1)).getProperties().get("LastModDate") != null) {
                    log.info("Last Modified Date : {}", new Object[]{ScriptUtil.dateToString(modDate)});
                }
                if (lastEventList.get((lastEventList.size() - 1)).getProperties().get("ClosedDate") != null) {
                    log.info("Closed Date : {}", new Object[]{ScriptUtil.dateToString(closedDate)});
                }
            }
        } catch (Exception ex) {
            log.error("Error {}", ex.getMessage());
        } finally {
            if (hasLoggedIntoJira) {
                //reader.logout();
            }
            if (exportToCsvFlag) {
                try {
                    writer.close();
                } catch (IOException e) {
                    log.error("Closing CSV Writer failed {}", e.getMessage());
                }
            }
        }

    }

    private static String[] getFieldHeaders(Template template) {
        TSIEvent event = template.getEventDefinition();
        List<String> fields = new ArrayList<>();
        for (Field field : event.getClass().getDeclaredFields()) {
            if (field.getName().equalsIgnoreCase("properties")) {
                for (String key : event.getProperties().keySet()) {
                    fields.add(key);
                }
            } else if (field.getName().equals("source") || field.getName().equals("sender")) {
                for (Field field1 : event.getSource().getClass().getDeclaredFields()) {
                    fields.add(field.getName() + "_" + field1.getName());
                }
            } else {
                fields.add(field.getName());
            }
        }
        return fields.toArray(new String[0]);
    }

    private static String[] getFieldValues(TSIEvent event, String[] header) {
        List<String> values = new ArrayList<>();
        for (String fieldName : header) {
            switch (fieldName) {
                case "title":
                    values.add(event.getTitle());
                    break;
                case "status":
                    values.add(event.getStatus());
                    break;
                case "severity":
                    values.add(event.getSeverity());
                    break;
                case "message":
                    values.add(event.getMessage());
                    break;
                case "createdAt":
                    values.add(event.getCreatedAt());
                    break;
                case "eventClass":
                    values.add(event.getEventClass());
                    break;
                case "sender_name":
                    values.add(event.getSender().getName());
                    break;
                case "sender_ref":
                    values.add(event.getSender().getRef());
                    break;
                case "sender_type":
                    values.add(event.getSender().getType());
                    break;
                case "source_name":
                    values.add(event.getSource().getName());
                    break;
                case "source_ref":
                    values.add(event.getSource().getRef());
                    break;
                case "source_type":
                    values.add(event.getSource().getType());
                    break;
                case "fingerprintFields":
                    values.add(String.join(",", event.getFingerprintFields()));
                    break;
                default:
                    values.add(event.getProperties().get(fieldName));
            }
        }
        return values.toArray(new String[0]);
    }

    private static Date convertToDate(String date) {
        Date dt = null;
        try {
            dt = new Date(Long.parseLong(date) * 1000L);
        } catch (Exception ex) {
            log.debug("Date conversion failed for date [{}]", date);
        }
        return dt;
    }

    private static void resetExportToCSVFlag() {
        exportToCsvFlag = false;
    }

}
