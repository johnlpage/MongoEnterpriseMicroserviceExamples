// Generate Entity Script - Model, Repository, Services, Controller

def className = System.getProperty('entity')
def idType = System.getProperty('idType') ?: 'String'
def deleteMode = System.getProperty('delete') != null

if (!className) {
    println ""
    println "Usage: mvn generate-sources -Pgenerate-entity -Dentity=Company [-DidType=String] [-Ddelete]"
    println ""
    println "Options:"
    println "  -Dentity=<ClassName>  Required. e.g., Company, ProductCategory"
    println "  -DidType=<Type>       Optional. Default: String"
    println "                        Supported: String, ObjectId, Long, UUID"
    println "  -Ddelete              Delete previously generated files instead of creating"
    println ""
    println "Examples:"
    println "  mvn generate-sources -Pgenerate-entity -Dentity=Company"
    println "  mvn generate-sources -Pgenerate-entity -Dentity=Company -DidType=ObjectId"
    println "  mvn generate-sources -Pgenerate-entity -Dentity=Order -DidType=Long"
    println "  mvn generate-sources -Pgenerate-entity -Dentity=Order -Ddelete"
    println ""
    throw new RuntimeException("Missing required parameter: -Dentity=<ClassName>")
}

// ===========================================================================
// CONFIGURATION
// ===========================================================================

def basePackage = "com.johnlpage.memex"
def packagePath = basePackage.replace('.', '/')
def templateBase = "templates"

// ID Type configuration
def idTypeMap = [
        'String'  : [type: 'String', import: ''],
        'ObjectId': [type: 'ObjectId', import: 'import org.bson.types.ObjectId;'],
        'Long'    : [type: 'Long', import: ''],
        'UUID'    : [type: 'UUID', import: 'import java.util.UUID;']
]

if (!idTypeMap.containsKey(idType)) {
    throw new RuntimeException("Unsupported idType: ${idType}. Supported: ${idTypeMap.keySet().join(', ')}")
}

def idConfig = idTypeMap[idType]

// Derive values from class name
def collectionName = className.replaceAll('([a-z])([A-Z])', '$1_$2').toLowerCase();
def idFieldName = className[0].toLowerCase() + className[1..-1] + 'Id'
// API path: VehicleInspection -> vehicleinspections, Order -> orders
def apiPath = className.toLowerCase()

// ===========================================================================
// DEFINE ALL GENERATED FILES
// ===========================================================================

def generatedFiles = [
        // Model
        [
                name    : 'Model',
                template: "${templateBase}/model/Model.java.template",
                output  : "src/main/java/${packagePath}/model/${className}.java"
        ],
        // Repository
        [
                name    : 'Repository',
                template: "${templateBase}/repository/Repository.java.template",
                output  : "src/main/java/${packagePath}/repository/${className}Repository.java"
        ],
        // Services
        [
                name    : 'DownstreamService',
                template: "${templateBase}/service/DownstreamService.java.template",
                output  : "src/main/java/${packagePath}/service/${className}DownstreamService.java"
        ],
        [
                name    : 'HistoryService',
                template: "${templateBase}/service/HistoryService.java.template",
                output  : "src/main/java/${packagePath}/service/${className}HistoryService.java"
        ],
        [
                name    : 'HistoryTriggerService',
                template: "${templateBase}/service/HistoryTriggerService.java.template",
                output  : "src/main/java/${packagePath}/service/${className}HistoryTriggerService.java"
        ],
        [
                name    : 'InvalidDataHandlerService',
                template: "${templateBase}/service/InvalidDataHandlerService.java.template",
                output  : "src/main/java/${packagePath}/service/${className}InvalidDataHandlerService.java"
        ],
        [
                name    : 'JsonLoaderService',
                template: "${templateBase}/service/JsonLoaderService.java.template",
                output  : "src/main/java/${packagePath}/service/${className}JsonLoaderService.java"
        ],
        [
                name    : 'PreWriteTriggerService',
                template: "${templateBase}/service/PreWriteTriggerService.java.template",
                output  : "src/main/java/${packagePath}/service/${className}PreWriteTriggerService.java"
        ],
        [
                name    : 'QueryService',
                template: "${templateBase}/service/QueryService.java.template",
                output  : "src/main/java/${packagePath}/service/${className}QueryService.java"
        ],
        [
                name    : 'PreflightConfig',
                template: "${templateBase}/service/PreflightConfig.java.template",
                output  : "src/main/java/${packagePath}/service/${className}PreflightConfig.java"
        ],
        // Controller
        [
                name    : 'Controller',
                template: "${templateBase}/controller/Controller.java.template",
                output  : "src/main/java/${packagePath}/controller/${className}Controller.java"
        ]
]

// ===========================================================================
// PLACEHOLDER REPLACEMENTS
// ===========================================================================

def replacements = [
        '__className__'     : className,
        '__collectionName__': collectionName,
        '__idFieldName__'   : idFieldName,
        '__apiPath__'       : apiPath,
        '__package__'       : basePackage,
        '__idType__'        : idType,
        '__idImport__'      : idConfig.import
]

// ===========================================================================
// DELETE MODE
// ===========================================================================

if (deleteMode) {
    println ""
    println "========================================="
    println "Deleting files for: ${className}"
    println "========================================="

    def deletedCount = 0
    generatedFiles.each { fileDef ->
        def file = new File(project.basedir, fileDef.output)
        if (file.exists()) {
            file.delete()
            println "  Deleted: ${fileDef.name}"
            deletedCount++
        } else {
            println "  Not found (skipped): ${fileDef.name}"
        }
    }

    println "========================================="
    println "Deleted ${deletedCount} file(s)"
    println "========================================="
    println ""
    return
}

// ===========================================================================
// GENERATE MODE
// ===========================================================================

println ""
println "========================================="
println "Generating files for: ${className}"
println "========================================="
println "  ID Type:      ${idType}"
println "  ID Field:     ${idFieldName}"
println "  Collection:   ${collectionName}"
println "  API Path:     /api/${apiPath}"
println "========================================="

def generatedCount = 0
def skippedCount = 0

generatedFiles.each { fileDef ->
    def templateFile = new File(project.basedir, fileDef.template)
    def outputFile = new File(project.basedir, fileDef.output)

    // Check template exists
    if (!templateFile.exists()) {
        println "  WARNING: Template not found for ${fileDef.name}"
        println "           Expected: ${templateFile}"
        skippedCount++
        return
    }

    // Check if output already exists
    if (outputFile.exists()) {
        println "  SKIPPED: ${fileDef.name} (already exists)"
        skippedCount++
        return
    }

    // Create output directory
    outputFile.parentFile.mkdirs()

    // Read and process template
    def template = templateFile.getText('UTF-8')
    def result = template

    replacements.each { placeholder, value ->
        result = result.replace(placeholder, value)
    }

    // Write output
    outputFile.text = result
    println "  Created: ${fileDef.name}"
    generatedCount++
}

println "========================================="
println "Generated: ${generatedCount}, Skipped: ${skippedCount}"
println "========================================="
println ""
println "API Endpoints created:"
println "  POST   /api/${apiPath}              - Create single record"
println "  POST   /api/${apiPath}/bulk         - Bulk load from JSON stream"
println "  GET    /api/${apiPath}/id/{id}      - Get by ID"
println "  GET    /api/${apiPath}/search       - Search with pagination"
println "  POST   /api/${apiPath}/query        - Native MongoDB query"
println "  POST   /api/${apiPath}/search/atlas - Atlas Search query"
println "  GET    /api/${apiPath}/json         - Stream all as JSON"
println "  GET    /api/${apiPath}/json/native  - Stream all as native JSON"
println "  GET    /api/${apiPath}/asOf         - Historical data query"
println ""
