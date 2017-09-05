# jira-tsi-bulkingestion-script

This is a jar application to load the historical issues from Jira to Truesight Intelligence.

## Description.

This Script ingests historical Jira issues into TSI. Based on the configuration present in the template(see in dist), this script reads the Jira issues and after converting into a TSI event (based on definitions in eventDefinition), it ingests the events to TSI.

## Prerequisite

* Java Sdk 1.8

## How to run ?

`
1.	Download or clone the repository.
2.	Move to dist directory.
	$ cd dist
3.	Open jiraTemplate.json file and input the required details in config section.
4.	Open command prompt and move to the same location.
5.	Run the jar file.
	$ java -jar jira-tsi-bulkingestion-script-0.0.1-SNAPSHOT.jar
6.	Read the output and provide the input in affirmative/negative.
`

## Configuration

The configuration template Json (jiraTemplate) contains 4 major fields/section.

1.	config
2.	filter
3.	eventDefinition
4.	Field definitions

The significance and functioning of each section is as below.

### 1.	Config

This has multiple fields related to basic input required for running the script, such as Jira hostname, userid, password, start and end date to retrieve the issues, TSI API token etc.  

### 2.	filter

If you want to filter the issues, you can provide a list of values to filter the issues based on values given for issue field.
Example
`
	"filter": {
        "status": ["Closed","BACKLOG"]
    },
 `
The above filter will search for issues with status "Closed" and "Backlog" within the configured startDateTime & endDateTime (Config fields).

### 3.	eventDefinition

The eventDefinition defines the mapping of TSI raw event field to the Jira issue field. based on this definition the Jira issue is converted to the TSI raw event and ingested into TSI.

### 4. Field Definition

If the field starts with '@' then this is a Jira Field definition. A Jira Field Definition contains a fieldId (Jira field key). It additionally can have a valueMap which would use the value from the valueMap in case the jira field value is present in valueMap.

Example
`
"@SUMMARY": {
   "fieldId": "summary"
},

`
## FAQ 

#### Why can't I see the complete mapping in the jiraTemplate ?

The reason jiraTemplate does not contain all the fields/properties is because it is not required.
The default EventDefinition, field mapping, and configurations are loaded even before reading the jiraTemplate. So when jiraTemplate is loaded, it basically overrides the values loaded by default.
So we should include only those fields in jiraTemplate which should have different value than the default one loaded.

#### Where is the default mapping Template? What values are loaded by default ?

You can navigate to the [template/jiraDefaultTemplate.json](https://github.com/boundary/jira-tsi-bulkingestion-script/blob/master/template/jiraDefaultTemplate.json) to see a copy of default template used. Please note that changing this template would not change the values loaded by default, this is just a copy for reference. Any change or modification should be made in the jiraTemplate.json available in the dist folder.

#### Can I add more Jira issue fields in the template ? How can I map it in the eventDefinition ?

Yes, maximum 128 fields can be mapped into the eventDefinition, so you can add a field definition
into the template and map it in the properties.

Step 1: Add a field Definition as a child field of the root template.
The field definition has following structure
`			
"@CUSTOM_FIELD": {
	"fieldId": "<Jira field Key>"
}
`
		    
Step 2: Add a property in the properties of eventDefinition and map to this field definition.

`
{
	.....
	"eventDefinition": {						  
		"properties": {
			"app_id": "Jira TSI Integration",
			"CustomFieldName":"@CUSTOM_FIELD"
		}
	},
	..... 
 }
 `	      