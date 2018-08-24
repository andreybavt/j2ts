# A tool to convert java class to TypeScript interfaces
   A tool performs static analysis of an input java file, extracting fields and subclasses. 

## Requirements
  - [maven](https://maven.apache.org/install.html)
## Installation
`mvn clean compile assembly:single`
## Usage
`j2ts #arguments#`

Arguments could be following:

MANDATORY:

**-p** - source code directory (or multiple with ":" as a delimeter) that will be scanned.

OPTIONAL:

**-i** - translated class fully qualified path (or any part of it)

**-sr** - source root. If `-tr` is specified, then target directories will repeat the structure of source from `-sr` directory.

**-tr** - target root. Directory where typescript files will be saved. Directory structure will repeat the one for Java code starting from `-sr` directory.

**-fr** - path to the frontend project root. Used to create preper import links.


### Usage note
If `-i` parameter is not specified, program will start in infinite mode, asking to provide new input after each translation.

If `-fr` is not specified, then the output will be printed to the standard output.

### Usage example
```bash
j2ts -p /Users/bob/dip/src/main/java/com/dataiku/dip/analysis/model \
-i MyJavaClass \
-sr /Users/bob/dip/src/main/java/com/dataiku/dip/ \
-tr /Users/bob/dip/src/main/front/src/app/model \
-fr /Users/bob/dip/src/main/front/src
```

