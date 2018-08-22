# A tool to convert java class to a TypeScript interfaces
   A tool performs static analysis of an input java file, extracting fields and subclasses. 

## Requirements
  - [maven](https://maven.apache.org/install.html)
## Installation
`mvn clean compile assembly:single`
## Usage
`j2ts JAVA_INPUT_FILE_PATH [TS_OUTPUT_FILE_PATH]`

1st argument is mandatory

2nd is optional, if it's present the result will be written to a corresponsing file,
 outherwise it'll be printed to standard output.

        
