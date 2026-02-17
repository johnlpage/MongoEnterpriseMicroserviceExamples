import groovy.json.JsonSlurper

/**
 * Generates Spring Data MongoDB model classes from a JSON sample file.
 * Supports both JSON arrays and newline-delimited JSON (NDJSON) formats.
 *
 * Usage:
 * mvn generate-sources -Pgenerate-models-from-json \
 *     -DjsonFile=path/to/sample.json \
 *     -DbasePackage=com.johnlpage.memex \
 *     -Dentity=NewEntity \
 *     -DidFieldName=myCustomId \
 *     -DsampleSize=100
 */

def packageName = basePackage ?: 'com.johnlpage.memex'
def newEntityName = System.getProperty('entity') ?: "NewEntity"
def idFieldName = System.getProperty('idFieldName') ?: newEntityName.toLowerCase() + "Id"
def sampleSize = (System.getProperty('sampleSize') ?: '100').toInteger()

// Entity-specific package (e.g., com.johnlpage.memex.NewEntity.model)
def entityPackage = "${packageName}.${newEntityName}.model"

// Configuration
def jsonFilePath = jsonFile ?: 'sample.json'
def modelDir = "src/main/java/${entityPackage.replace('.', '/')}"
def outputDir = new File(project.basedir, modelDir)

// Derive annotation and util packages from base package
def annotationPackage = "${packageName}.util"
def utilPackage = "${packageName}.util"

println "=========================================="
println "MongoDB Model Generator from JSON"
println "=========================================="
println "JSON File: ${jsonFilePath}"
println "Base Package: ${packageName}"
println "Entity Package: ${entityPackage}"
println "Entity Name: ${newEntityName}"
println "ID Field Name: ${idFieldName}"
println "Sample Size: ${sampleSize}"
println "Output Directory: ${outputDir}"
println "=========================================="

// Ensure output directory exists
outputDir.mkdirs()

// Read and parse JSON
def jsonFileObj = new File(project.basedir, jsonFilePath)
if (!jsonFileObj.exists()) {
    throw new RuntimeException("JSON file not found: ${jsonFileObj.absolutePath}")
}

def jsonSlurper = new JsonSlurper()

/**
 * Check if a value is "empty" (null, empty list, empty map, empty string)
 */
def isEmpty = { Object obj ->
    if (obj == null) return true
    if (obj instanceof List && ((List) obj).isEmpty()) return true
    if (obj instanceof Map && ((Map) obj).isEmpty()) return true
    if (obj instanceof String && ((String) obj).trim().isEmpty()) return true
    return false
}

/**
 * Deep merge two objects, combining fields from both.
 * Uses a stack-based approach to avoid closure recursion issues.
 */
def deepMerge = null
deepMerge = { Object obj1, Object obj2 ->
    // Handle nulls and empties - always prefer the non-empty one
    if (isEmpty(obj1)) return obj2
    if (isEmpty(obj2)) return obj1

    // Both are Maps - merge their fields recursively
    if ((obj1 instanceof Map) && (obj2 instanceof Map)) {
        Map map1 = (Map) obj1
        Map map2 = (Map) obj2
        Map result = new LinkedHashMap(map1)

        map2.each { key, value ->
            if (result.containsKey(key)) {
                result.put(key, deepMerge.call(result.get(key), value))
            } else {
                result.put(key, value)
            }
        }
        return result
    }

    // Both are Lists
    if ((obj1 instanceof List) && (obj2 instanceof List)) {
        List list1 = (List) obj1
        List list2 = (List) obj2

        // Empty list - use the other
        if (list1.isEmpty()) return list2
        if (list2.isEmpty()) return list1

        def first1 = list1.get(0)
        def first2 = list2.get(0)

        // If one has objects and one doesn't, prefer objects
        if (!(first1 instanceof Map) && (first2 instanceof Map)) {
            return list2
        }
        if ((first1 instanceof Map) && !(first2 instanceof Map)) {
            return list1
        }

        // If both contain objects, merge the first elements
        if ((first1 instanceof Map) && (first2 instanceof Map)) {
            return [deepMerge.call(first1, first2)]
        }

        // Both are scalar lists - return first
        return list1
    }

    // One is List, one is not - prefer List with content
    if ((obj1 instanceof List) && !((List) obj1).isEmpty()) return obj1
    if ((obj2 instanceof List) && !((List) obj2).isEmpty()) return obj2

    // Default: return first non-empty
    return obj1
}

/**
 * Parse JSON file - handles both JSON array and newline-delimited JSON (NDJSON)
 */
def parseJsonFile = { File file, JsonSlurper slurper, int maxDocs ->
    def documents = []

    // Read first non-whitespace character to determine format
    Character firstChar = null
    file.withReader('UTF-8') { reader ->
        int ch
        while ((ch = reader.read()) != -1) {
            char c = (char) ch
            if (!Character.isWhitespace(c)) {
                firstChar = c
                break
            }
        }
    }

    if (firstChar == (char) '[') {
        // JSON array format
        println "Detected: JSON array format"
        def parsed = slurper.parse(file)
        if (parsed instanceof List) {
            documents = ((List) parsed).take(maxDocs)
        } else {
            documents = [parsed]
        }
    } else if (firstChar == (char) '{') {
        // Newline-delimited JSON (NDJSON) format
        println "Detected: Newline-delimited JSON (NDJSON) format"
        file.eachLine('UTF-8') { line ->
            if (documents.size() < maxDocs) {
                String trimmed = line.trim()
                if (trimmed && trimmed.startsWith('{')) {
                    try {
                        documents.add(slurper.parseText(trimmed))
                    } catch (Exception e) {
                        println "  Warning: Failed to parse line: ${e.message.take(50)}"
                    }
                }
            }
        }
    } else {
        throw new RuntimeException("Unrecognized JSON format. File should start with '[' (array) or '{' (NDJSON)")
    }

    println "Parsed ${documents.size()} document(s)"
    return documents
}

def jsonDocuments = parseJsonFile(jsonFileObj, jsonSlurper, sampleSize)

if (jsonDocuments.isEmpty()) {
    throw new RuntimeException("No valid JSON documents found in file")
}

/**
 * Merge multiple sample documents to get a comprehensive field list
 */
def mergeSamples = { List samples ->
    if (samples == null || samples.isEmpty()) {
        return new LinkedHashMap()
    }

    println "\nMerging ${samples.size()} document(s) to discover all fields..."

    Map merged = new LinkedHashMap()
    samples.each { sample ->
        merged = (Map) deepMerge.call(merged, sample)
    }

    return merged
}

/**
 * Print the merged structure for debugging
 */
def printStructure = null
printStructure = { Object obj, String indent ->
    if (obj instanceof Map) {
        ((Map) obj).each { key, value ->
            if (value instanceof Map) {
                println "${indent}${key}: {object with ${((Map) value).size()} fields}"
                printStructure.call(value, indent + "  ")
            } else if (value instanceof List) {
                List listVal = (List) value
                if (listVal.isEmpty()) {
                    println "${indent}${key}: [EMPTY ARRAY] <-- Warning: no sample data found"
                } else if (listVal.get(0) instanceof Map) {
                    println "${indent}${key}: [array of objects with ${((Map) listVal.get(0)).size()} fields]"
                    printStructure.call(listVal.get(0), indent + "  ")
                } else {
                    println "${indent}${key}: [array of ${listVal.get(0)?.getClass()?.getSimpleName() ?: 'null'}]"
                }
            } else {
                def typeName = value?.getClass()?.getSimpleName() ?: 'null'
                println "${indent}${key}: ${typeName}"
            }
        }
    }
}

// Merge all documents to get comprehensive schema
def sampleObject = mergeSamples(jsonDocuments)

println "\n--- Merged Schema Structure ---"
printStructure(sampleObject, "")
println "-------------------------------\n"

/**
 * Convert a field name to a proper Java class name
 */
def toClassName = { String name ->
    if (name == '_id') return 'Id'
    return name.split(/[_\-\s]+/)
            .collect { it.capitalize() }
            .join('')
}

/**
 * Convert a field name to a proper Java field name (camelCase)
 */
def toFieldName = { String name, String idFldName ->
    if (name == '_id') return idFldName
    /*
    // If the input data had underscores we could map to camelCase
    // Need to also add @JsonProperty too
    def parts = name.split(/[_\-\s]+/)
    def first = parts[0].toLowerCase()
    def rest = parts.drop(1).collect { it.capitalize() }.join('')
    return first + rest*/
    return name;
}

/**
 * Determine if a field name needs @JsonProperty annotation.
 *
 * Jackson derives JSON property names from getter methods using JavaBean conventions.
 * When Lombok generates getters, names like "SICCode" become "getSICCode()" which
 * Jackson interprets as property "siccode" (lowercasing consecutive capitals).
 *
 * This causes a mismatch when the JSON has "SICCode" but Jackson expects "siccode",
 * resulting in the field being routed to @JsonAnySetter (payload map) instead.
 *
 * Problem cases:
 * - Field starts with uppercase: SICCode -> getSICCode() -> Jackson expects "siccode"
 * - Field starts with single lowercase + uppercase: uRI -> getURI() -> Jackson expects "uri"
 *
 * Safe cases:
 * - Field starts with 2+ lowercase letters: companyName -> getCompanyName() -> "companyName" ✓
 */
def needsJsonPropertyAnnotation = { String fieldName ->
    if (fieldName == null || fieldName.isEmpty()) {
        return false
    }

    char first = fieldName.charAt(0)

    // Starts with uppercase letter - NEEDS @JsonProperty
    // e.g., "SICCode", "URL", "HTMLParser"
    if (Character.isUpperCase(first)) {
        return true
    }

    // Starts with single lowercase followed by uppercase - NEEDS @JsonProperty
    // e.g., "uRI", "xCoordinate", "iPhone"
    if (fieldName.length() > 1 && Character.isUpperCase(fieldName.charAt(1))) {
        return true
    }

    // Safe - standard camelCase starting with 2+ lowercase letters
    return false
}

/**
 * Determine the Java type for a JSON value
 */
def determineType = null
determineType = { String fieldName, Object value, String parentClassName ->
    if (value == null) {
        return [type: 'Object', isComplex: false]
    }

    if (value instanceof String) {
        String strVal = (String) value

        if (strVal ==~ /^\d{4}-\d{2}-\d{2}.*/ || strVal ==~ /.*T\d{2}:\d{2}:\d{2}.*/) {
            return [type: 'Date', isComplex: false, imports: ['java.util.Date']]
        }
        return [type: 'String', isComplex: false]
    }

    if (value instanceof Integer) {
        return [type: 'Integer', isComplex: false]
    }

    if (value instanceof Long) {
        return [type: 'Long', isComplex: false]
    }

    if (value instanceof Double || value instanceof Float || value instanceof BigDecimal) {
        return [type: 'Double', isComplex: false]
    }

    if (value instanceof Boolean) {
        return [type: 'Boolean', isComplex: false]
    }

    if (value instanceof List) {
        List listVal = (List) value
        if (listVal.isEmpty()) {
            println "  WARNING: Field '${fieldName}' has empty array in all samples - using List<Object>"
            return [type: 'List<Object>', isComplex: false, imports: ['java.util.List']]
        }
        def firstElement = listVal.get(0)

        // If the array element is a Map (object), create an embedded class for it
        if (firstElement instanceof Map) {
            def embeddedClassName = parentClassName + toClassName(fieldName)
            println "  Found: ${fieldName} -> List<${embeddedClassName}> (embedded class with ${((Map) firstElement).size()} fields)"
            return [
                    type         : "List<${embeddedClassName}>",
                    isComplex    : false,
                    imports      : ['java.util.List'],
                    embeddedClass: [name: embeddedClassName, fields: firstElement]
            ]
        }

        // For simple types - determine the element type
        def elementType = determineType.call(fieldName, firstElement, parentClassName)
        return [
                type     : "List<${elementType.type}>",
                isComplex: false,
                imports  : ['java.util.List'] + (elementType.imports ?: [])
        ]
    }

    if (value instanceof Map) {
        def embeddedClassName = parentClassName + toClassName(fieldName)
        return [
                type         : embeddedClassName,
                isComplex    : true,
                embeddedClass: [name: embeddedClassName, fields: value]
        ]
    }

    return [type: 'Object', isComplex: false]
}

/**
 * Process a class and its nested classes recursively
 */
def processClass = null
processClass = { Map classes, String className, Map fields, boolean isRoot, String annotationPkg, String utilPkg, String idFieldNameParam ->
    def classInfo = [
            name             : className,
            isRoot           : isRoot,
            fields           : [],
            imports          : new HashSet([
                    'lombok.Data',
                    'org.springframework.data.mongodb.core.mapping.Field',
                    'com.fasterxml.jackson.annotation.JsonAnySetter',
                    'com.fasterxml.jackson.annotation.JsonAnyGetter',
                    'com.fasterxml.jackson.annotation.JsonInclude',
                    'java.util.Map',
                    'java.util.HashMap',
                    'org.springframework.data.annotation.Id',
                    "${utilPkg}.ObjectConverter"
            ]),
            annotationPackage: annotationPkg,
            utilPackage      : utilPkg,
            idFieldName      : idFieldNameParam
    ]

    if (isRoot) {
        ((Set) classInfo.imports).addAll([
                'org.springframework.data.mongodb.core.mapping.Document',
                'org.springframework.data.annotation.Version',
                'org.springframework.data.annotation.Transient',
                "${annotationPkg}.DeleteFlag"
        ])
    }

    fields.each { fieldName, fieldValue ->
        def typeInfo = determineType.call((String) fieldName, fieldValue, className)
        def javaFieldName = toFieldName((String) fieldName, idFieldNameParam)

        // Check if this field needs @JsonProperty annotation
        def needsJsonProperty = needsJsonPropertyAnnotation(javaFieldName)

        if (needsJsonProperty) {
            ((Set) classInfo.imports).add('com.fasterxml.jackson.annotation.JsonProperty')
            println "  Note: Field '${javaFieldName}' needs @JsonProperty annotation (non-standard naming)"
        }

        def fieldInfo = [
                originalName     : fieldName,
                javaName         : javaFieldName,
                type             : typeInfo.type,
                isId             : typeInfo.isId ?: false,
                needsJsonProperty: needsJsonProperty
        ]

        if (typeInfo.imports) {
            ((Set) classInfo.imports).addAll((List) typeInfo.imports)
        }

        if (fieldInfo.isId) {
            ((Set) classInfo.imports).add('org.springframework.data.annotation.Id')
        }

        ((List) classInfo.fields).add(fieldInfo)

        // Process embedded classes for both direct objects AND array elements
        if (typeInfo.embeddedClass) {
            processClass.call(classes, (String) typeInfo.embeddedClass.name, (Map) typeInfo.embeddedClass.fields, false, annotationPkg, utilPkg, idFieldNameParam)
        }
    }

    classes.put(className, classInfo)
}

/**
 * Generate Java class file content
 */
def generateClassContent = { Map classInfo, String pkgName, String collectionName ->
    def sb = new StringBuilder()

    // Package declaration
    sb.append("package ${pkgName};\n\n")

    // Imports (sorted)
    ((Set) classInfo.imports).sort().each { imp ->
        sb.append("import ${imp};\n")
    }
    sb.append("\n")

    // Class Javadoc
    sb.append("/**\n")
    if (classInfo.isRoot) {
        sb.append(" * Root document class for the '${collectionName}' collection.\n")
        sb.append(" * <p>\n")
        sb.append(" * Features:\n")
        sb.append(" * - Optimistic locking via @Version\n")
        sb.append(" * - Soft delete support via @DeleteFlag\n")
        sb.append(" * - Flexible schema via payload map for unmapped fields\n")
    } else {
        sb.append(" * Embedded document class.\n")
    }
    sb.append(" * </p>\n")
    sb.append(" * <p>\n")
    sb.append(" * All classes include a 'payload' map to capture unmapped fields,\n")
    sb.append(" * supporting schema flexibility and evolution.\n")
    sb.append(" * </p>\n")
    sb.append(" * Generated from JSON sample - review and adjust as needed.\n")
    sb.append(" */\n")

    // Annotations
    sb.append("@Data\n")
    if (classInfo.isRoot) {
        sb.append("@Document(collection = \"${collectionName}\")\n")
    }

    // Class declaration
    sb.append("public class ${classInfo.name} {\n\n")

    if (classInfo.isRoot) {
        // Add ID field first
        sb.append("    @Id\n")
        sb.append("    private String ${classInfo.idFieldName};\n\n")

        // Add version field
        sb.append("    /**\n")
        sb.append("     * Optimistic locking version field.\n")
        sb.append("     * Automatically incremented by Spring Data MongoDB on each save.\n")
        sb.append("     */\n")
        sb.append("    @Version\n")
        sb.append("    private Long version;\n\n")
    }

    // Regular fields (skip _id as we handle it separately for root)
    ((List) classInfo.fields).each { field ->
        Map fieldMap = (Map) field
        if (classInfo.isRoot && fieldMap.javaName == idFieldName) {
            // Skip - already handled above
            return
        }

        // Add @JsonProperty annotation if needed for non-standard field names
        if (fieldMap.needsJsonProperty) {
            sb.append("    /**\n")
            sb.append("     * @JsonProperty required because field name starts with uppercase or\n")
            sb.append("     * single lowercase + uppercase (e.g., 'SICCode', 'uRI').\n")
            sb.append("     * Without it, Jackson would expect '${fieldMap.javaName.toLowerCase()}' in JSON\n")
            sb.append("     * due to JavaBean getter naming conventions (get${fieldMap.javaName.capitalize()} -> ${fieldMap.javaName.toLowerCase()}).\n")
            sb.append("     */\n")
            sb.append("    @JsonProperty(\"${fieldMap.originalName}\")\n")
        }

        sb.append("    private ${fieldMap.type} ${fieldMap.javaName};\n\n")
    }

    if (classInfo.isRoot) {
        // Add delete flag (root only)
        sb.append("    /**\n")
        sb.append("     * Use this to flag from the JSON that we want to remove the record.\n")
        sb.append("     * Not persisted to MongoDB.\n")
        sb.append("     */\n")
        sb.append("    @Transient\n")
        sb.append("    @DeleteFlag\n")
        sb.append("    private Boolean deleted;\n\n")
    }

    // Add payload map and methods (for ALL classes)

    sb.append("    /**\n")
    sb.append("     * Captures any fields not explicitly mapped to class fields.\n")
    sb.append("     * Supports schema flexibility and evolution.\n")
    sb.append("     * Only persisted/serialized when non-empty.\n")
    sb.append("     */\n")
    sb.append("    @Field(write = Field.Write.NON_NULL)\n")
    sb.append("    private Map<String, Object> payload;\n\n")

    sb.append("    @JsonAnySetter\n")
    sb.append("    public void set(String key, Object value) {\n")
    sb.append("        if (payload == null) {\n")
    sb.append("            payload = new HashMap<String, Object>();\n")
    sb.append("        }\n")
    sb.append("        payload.put(key, ObjectConverter.convertObject(value));\n")
    sb.append("    }\n\n")

    sb.append("    @JsonAnyGetter\n")
    sb.append("    public Map<String, Object> getPayload() {\n")
    sb.append("        return payload;\n")
    sb.append("    }\n\n")

    sb.append("    /**\n")
    sb.append("     * Helper method to safely add to payload from your own code\n")
    sb.append("     */\n")
    sb.append("    public void addToPayload(String key, Object value) {\n")
    sb.append("        set(key, value);\n")
    sb.append("    }\n")

    sb.append("}\n")

    return sb.toString()
}

// Main execution
def rootClassName = newEntityName
Map classesMap = new LinkedHashMap()

println "Processing JSON structure..."
processClass(classesMap, rootClassName, (Map) sampleObject, true, annotationPackage, utilPackage, idFieldName)

println "\nGenerating ${classesMap.size()} class(es):\n"

classesMap.each { className, classInfo ->
    def collectionName = ((String) className).toLowerCase()
    def classContent = generateClassContent((Map) classInfo, entityPackage, collectionName)
    def outputFile = new File(outputDir, "${className}.java")
    outputFile.text = classContent

    def marker = ((Map) classInfo).isRoot ? " [ROOT]" : " [EMBEDDED]"
    println "  ✓ ${className}.java${marker}"
}

println "\n=========================================="
println "Generation complete!"
println "=========================================="
println "\nGenerated files are in: ${outputDir}"
println ""
