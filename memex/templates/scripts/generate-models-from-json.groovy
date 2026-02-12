import groovy.json.JsonSlurper

/**
 * Generates Spring Data MongoDB model classes from a JSON sample file.
 *
 * Usage:
 * mvn generate-sources -Pgenerate-models-from-json \
 *     -DjsonFile=path/to/sample.json \
 *     -DbasePackage=com.johnlpage.memex.model \
 *     -DcollectionName=myCollection \
 *     -DidFieldName=myCustomId
 */

// Configuration
def jsonFilePath = jsonFile ?: 'sample.json'
def packageName = basePackage ?: 'com.johnlpage.memex.model'
def collection = collectionName ?: 'documents'
def customIdFieldName = idFieldName ?: 'id'  // Default to 'id' if not specified
def outputDir = new File(project.basedir, "src/main/java/${packageName.replace('.', '/')}")

// Derive annotation package from base package (assumes structure like com.company.project.model -> com.company.project.annotation)
def annotationPackage = packageName.replaceAll(/\.model$/, '.annotation')
def utilPackage = packageName.replaceAll(/\.model$/, '.util')

println "=========================================="
println "MongoDB Model Generator from JSON"
println "=========================================="
println "JSON File: ${jsonFilePath}"
println "Package: ${packageName}"
println "Collection: ${collection}"
println "ID Field Name: ${customIdFieldName}"
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
def jsonContent = jsonSlurper.parse(jsonFileObj)

// Handle both single object and array of objects
def sampleObject = jsonContent instanceof List ? jsonContent[0] : jsonContent

// Track all classes to generate
def classesToGenerate = [:]

/**
 * Convert a field name to a proper Java class name
 */
def toClassName(String name) {
    if (name == '_id') return 'Id'
    return name.split(/[_\-\s]+/)
            .collect { it.capitalize() }
            .join('')
}

/**
 * Convert a field name to a proper Java field name (camelCase)
 */
def toFieldName(String name) {
    if (name == '_id') return customIdFieldName
    def parts = name.split(/[_\-\s]+/)
    def first = parts[0].toLowerCase()
    def rest = parts.drop(1).collect { it.capitalize() }.join('')
    return first + rest
}

/**
 * Determine the Java type for a JSON value
 */
def determineType(String fieldName, Object value, String parentClassName) {
    if (value == null) {
        return [type: 'Object', isComplex: false, needsFieldAnnotation: false]
    }

    switch (value) {
        case String:
            if (fieldName == '_id' || (value ==~ /^[a-f0-9]{24}$/)) {
                return [type: 'String', isComplex: false, needsFieldAnnotation: fieldName == '_id', isId: fieldName == '_id']
            }
            if (value ==~ /^\d{4}-\d{2}-\d{2}.*/ || value ==~ /.*T\d{2}:\d{2}:\d{2}.*/) {
                return [type: 'Instant', isComplex: false, needsFieldAnnotation: false, imports: ['java.time.Instant']]
            }
            return [type: 'String', isComplex: false, needsFieldAnnotation: false]

        case Integer:
        case Long:
            if (value instanceof Integer && value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE) {
                return [type: 'Integer', isComplex: false, needsFieldAnnotation: false]
            }
            return [type: 'Long', isComplex: false, needsFieldAnnotation: false]

        case Double:
        case Float:
        case BigDecimal:
            return [type: 'Double', isComplex: false, needsFieldAnnotation: false]

        case Boolean:
            return [type: 'Boolean', isComplex: false, needsFieldAnnotation: false]

        case List:
            if (value.isEmpty()) {
                return [type: 'List<Object>', isComplex: false, needsFieldAnnotation: false, imports: ['java.util.List']]
            }
            def firstElement = value[0]
            def elementType = determineType(fieldName, firstElement, parentClassName)
            if (elementType.isComplex) {
                return [
                        type                : "List<${elementType.type}>",
                        isComplex           : false,
                        needsFieldAnnotation: false,
                        imports             : ['java.util.List'] + (elementType.imports ?: []),
                        embeddedClass       : elementType.embeddedClass
                ]
            }
            return [
                    type                : "List<${elementType.type}>",
                    isComplex           : false,
                    needsFieldAnnotation: false,
                    imports             : ['java.util.List'] + (elementType.imports ?: [])
            ]

        case Map:
            def embeddedClassName = parentClassName + toClassName(fieldName)
            return [
                    type                : embeddedClassName,
                    isComplex           : true,
                    needsFieldAnnotation: false,
                    embeddedClass       : [name: embeddedClassName, fields: value]
            ]

        default:
            return [type: 'Object', isComplex: false, needsFieldAnnotation: false]
    }
}

/**
 * Process a class and its nested classes recursively
 */
def processClass(String className, Map<String, Object> fields, boolean isRoot, String annotationPkg, String utilPkg, String idFieldNameParam) {
    def classInfo = [
            name             : className,
            isRoot           : isRoot,
            fields           : [],
            imports          : new HashSet(['lombok.Data']),
            annotationPackage: annotationPkg,
            utilPackage      : utilPkg,
            idFieldName      : idFieldNameParam
    ]

    if (isRoot) {
        classInfo.imports.addAll([
                'org.springframework.data.mongodb.core.mapping.Document',
                'org.springframework.data.annotation.Version',
                'org.springframework.data.annotation.Transient',
                'com.fasterxml.jackson.annotation.JsonAnySetter',
                'com.fasterxml.jackson.annotation.JsonAnyGetter',
                'java.util.Map',
                'java.util.HashMap',
                "${annotationPkg}.DeleteFlag",
                "${utilPkg}.ObjectConverter"
        ])
    }

    fields.each { fieldName, fieldValue ->
        def typeInfo = determineType(fieldName, fieldValue, className)
        def javaFieldName = toFieldName(fieldName)
        def needsFieldAnnotation = (fieldName != javaFieldName && fieldName != '_id')

        def fieldInfo = [
                originalName        : fieldName,
                javaName            : javaFieldName,
                type                : typeInfo.type,
                needsFieldAnnotation: needsFieldAnnotation,
                isId                : typeInfo.isId ?: false
        ]

        if (typeInfo.imports) {
            classInfo.imports.addAll(typeInfo.imports)
        }

        if (needsFieldAnnotation) {
            classInfo.imports.add('org.springframework.data.mongodb.core.mapping.Field')
        }

        if (fieldInfo.isId) {
            classInfo.imports.add('org.springframework.data.annotation.Id')
        }

        classInfo.fields.add(fieldInfo)

        if (typeInfo.embeddedClass) {
            processClass(typeInfo.embeddedClass.name, typeInfo.embeddedClass.fields, false, annotationPkg, utilPkg, idFieldNameParam)
        }
    }

    classesToGenerate[className] = classInfo
}

/**
 * Generate Java class file content
 */
def generateClassContent(Map classInfo, String packageName, String collectionName) {
    def sb = new StringBuilder()

    // Package declaration
    sb.append("package ${packageName};\n\n")

    // Imports (sorted)
    classInfo.imports.sort().each { imp ->
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
    classInfo.fields.each { field ->
        if (classInfo.isRoot && field.isId) {
            // Skip - already handled above
            return
        }

        if (field.needsFieldAnnotation) {
            sb.append("    @Field(\"${field.originalName}\")\n")
        }
        sb.append("    private ${field.type} ${field.javaName};\n\n")
    }

    if (classInfo.isRoot) {
        // Add delete flag
        sb.append("    /**\n")
        sb.append("     * Use this to flag from the JSON that we want to remove the record.\n")
        sb.append("     * Not persisted to MongoDB.\n")
        sb.append("     */\n")
        sb.append("    @Transient\n")
        sb.append("    @DeleteFlag\n")
        sb.append("    private Boolean deleted;\n\n")

        // Add payload map and methods
        sb.append("    /**\n")
        sb.append("     * Use this to capture any fields not captured explicitly.\n")
        sb.append("     * MongoDB's flexibility makes this easy to handle schema evolution.\n")
        sb.append("     */\n")
        sb.append("    private Map<String, Object> payload = new HashMap<>();\n\n")

        sb.append("    /**\n")
        sb.append("     * Captures any JSON field not explicitly mapped to a class field.\n")
        sb.append("     *\n")
        sb.append("     * @param key   the field name\n")
        sb.append("     * @param value the field value\n")
        sb.append("     */\n")
        sb.append("    @JsonAnySetter\n")
        sb.append("    public void set(String key, Object value) {\n")
        sb.append("        payload.put(key, ObjectConverter.convertObject(value));\n")
        sb.append("    }\n\n")

        sb.append("    /**\n")
        sb.append("     * Returns all unmapped fields for JSON serialization.\n")
        sb.append("     *\n")
        sb.append("     * @return map of unmapped field names to values\n")
        sb.append("     */\n")
        sb.append("    @JsonAnyGetter\n")
        sb.append("    public Map<String, Object> getPayload() {\n")
        sb.append("        return payload;\n")
        sb.append("    }\n")
    }

    sb.append("}\n")

    return sb.toString()
}

// Main execution
def rootClassName = toClassName(collection)
println "\nProcessing JSON structure..."
processClass(rootClassName, sampleObject, true, annotationPackage, utilPackage, customIdFieldName)

println "\nGenerating ${classesToGenerate.size()} class(es):\n"

classesToGenerate.each { className, classInfo ->
    def classContent = generateClassContent(classInfo, packageName, collection)
    def outputFile = new File(outputDir, "${className}.java")
    outputFile.text = classContent

    def marker = classInfo.isRoot ? " [ROOT]" : " [EMBEDDED]"
    println "  ✓ ${className}.java${marker}"
}

println "\n=========================================="
println "Generation complete!"
println "=========================================="
println "\nGenerated files are in: ${outputDir}"
println "\nRequired supporting classes (create if not present):"
println "  - ${annotationPackage}.DeleteFlag"
println "  - ${utilPackage}.ObjectConverter"
println "\nNext steps:"
println "  1. Review the generated classes"
println "  2. Add validation annotations as needed (@NotNull, @Size, etc.)"
println "  3. Add indexes using @Indexed or @CompoundIndex"
println "  4. Consider adding @Builder from Lombok if needed"
println ""
