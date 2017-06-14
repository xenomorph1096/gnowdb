(ns gnowdb.spec.files
  (:gen-class)
  (:require [gnowdb.neo4j.gneo :as gneo]
  			[digest :as digest]
  			[clojure.java.shell :refer [sh]]
  			[gnowdb.spec.workspaces :as workspaces]
  			[pantomime.mime :refer [mime-type-of]]
  			[pantomime.extract :as extract]
  			[clojure.java.io :as io]
  )
)

(defn- createFileClass
	[] 
	(gneo/createClass :className "GDB_File" :classType "NODE" :isAbstract? false :subClassOf ["GDB_Node"] :properties {} :execute? true)
	(gneo/createAttributeType :_name "GDB_Path" :_datatype "java.lang.String" :subTypeOf [] :execute? true)
	(gneo/createAttributeType :_name "GDB_Extension" :_datatype "java.lang.String" :subTypeOf [] :execute? true)
	(gneo/createAttributeType :_name "GDB_Size" :_datatype "java.lang.Long" :subTypeOf [] :execute? true)
	(gneo/createAttributeType :_name "GDB_MimeType" :_datatype "java.lang.String" :subTypeOf [] :execute? true)
	(gneo/createAttributeType :_name "GDB_MD5" :_datatype "java.lang.String" :subTypeOf [] :execute? true)
	;(gneo/createAttributeType :_name "GDB_FileID" :_datatype "java.lang.String" :subTypeOf [] :execute? true)
	(gneo/addClassAT :_atname "GDB_Path" :className "GDB_File" :execute? true)
	(gneo/addClassAT :_atname "GDB_Extension" :className "GDB_File" :execute? true)
	(gneo/addClassAT :_atname "GDB_Size" :className "GDB_File" :execute? true)	
	(gneo/addClassAT :_atname "GDB_MD5" :className "GDB_File" :execute? true)
	(gneo/addClassAT :_atname "GDB_MimeType" :className "GDB_File" :execute? true)
	;(gneo/addClassAT :_atname "GDB_FileID" :className "GDB_File" :execute? true)
)

(defn- mkdir
	"filePath e.g. 1/2/3"
	[filePath]
	(sh "mkdir -p" filePath)
)

(defn- copyFile
	[srcPath destPath]
	(sh "cp" srcPath destPath)
)

(defn- copyFileToDir
	[srcPath filePath]
	(mkdir filePath)
	(copyFile srcPath filePath)
	)

(defn- derivePath
	[GDB_MD5]
	(let [last3DigitString (subs GDB_MD5 (- (count GDB_MD5) 3)) 
		  filePath (str (subs last3DigitString 2 3) "/" (subs last3DigitString 1 2) "/" (subs last3DigitString 0 1))]
		  filePath
	)
)

(defn- removeFilefromDirectory
	[fileName]
	(sh "rm" (str (derivePath (digest/md5 fileName)) "/" fileName))
)

(defn- getMetaData 
	[filePath]
	(extract/parse filePath)
)

(defn- deriveFileName
	[filePath]
	(subs filePath (inc (clojure.string/last-index-of filePath "/")))
)

(defn fileExists
	"Returns true if the given workspace contains the file else false"
	[& {:keys [:fileName :workspaceName :workspaceClass]}]
	(workspaces/resourceExists :resourceIDMap {"GDB_DisplayName" fileName} :resourceClass "GDB_File" :workspaceClass workspaceClass :workspaceName workspaceName)
)

(defn createFileInstance 
	":fileSrcPath should include the filename as well e.g. src/gnowdb/core.clj.
	 :author is the name of the user uploading file .
	 :memberOfWorkspace is a vector containing names of groupworkspaces which will
	  contain the file.
	  if PersonalWorkspace of author already contains the file then the file instance is not created.
	  if GroupWorkspace already contains the file then the file is not published to that group."
	[& {:keys[:fileSrcPath :author :memberOfWorkspace] :or {:author "ADMIN" :memberOfWorkspace []}}]
	(let [fileName (deriveFileName fileSrcPath) filePath (derivePath (digest/md5 fileName))]
		(if (not (fileExists :fileName fileName :workspaceName author :workspaceClass "GDB_PersonalWorkspace"))
			(do
				(gneo/createNodeClassInstances :className "GDB_File" :nodeList 	[{
																					"GDB_DisplayName" fileName 
																					"GDB_Extension" (subs fileName (inc (clojure.string/last-index-of fileName ".")))
																					"GDB_Size" (.length (java.io.File. filePath))
																					"GDB_Path" filePath
																					"GDB_MD5" (digest/md5 fileName)
																				;	"GDB_FileID" 
																					"GDB_MimeType" (mime-type-of (str fileName))
																					"GDB_ModifiedAt" (.toString (new java.util.Date (.lastModified (java.io.File. filePath))))
																					"GDB_CreatedAt" (.toString (new java.util.Date))
																				}]
				)
				(gneo/createRelationClassInstances :className "GDB_CreatedBy" :relList 	[{
																							:fromClassName "GDB_File"
																							:fromPropertyMap {"GDB_DisplayName" fileName}
																							:toClassName "GDB_PersonalWorkspace"
																							:toPropertyMap {"GDB_DisplayName" author}
																							:propertyMap {}
																						}]
				)
				(gneo/createRelationClassInstances :className "GDB_LastModifiedBy" :relList [{
																								:fromClassName "GDB_File"
																								:fromPropertyMap {"GDB_DisplayName" fileName}
																								:toClassName "GDB_PersonalWorkspace"
																								:toPropertyMap {"GDB_DisplayName" author}
																								:propertyMap {}
																							}]
				)
				(workspaces/publishToPersonalWorkspace :username author :resourceIDMap {"GDB_DisplayName" fileName} :resourceClass "GDB_File")
				(if (not (empty? memberOfWorkspace))
					(
						map (fn [groupName]
								(if (not (fileExists :fileName fileName :workspaceName groupName :workspaceClass "GDB_GroupWorkspace"))
									(workspaces/publishToGroup :username author :groupName groupName :resourceIDMap {"GDB_DisplayName" fileName} :resourceClass "GDB_File")
							 	)
						 	)
						memberOfWorkspace
					)
				)
			)
			(println "Personal Workspace of author already contains the file!")
		)		
	)
)

(defn deleteFileFromGroupWorkspace
	"Delete a file from given workspace"
	[& {:keys [:username :groupName :fileName]}]
	(
		workspaces/deleteFromGroup :username username :groupName groupName :resourceIDMap {"GDB_DisplayName" fileName} :resourceClass "GDB_File"
	)
)

(defn deleteFileFromPersonalWorkspace
	"Delete a file from given workspace"
	[& {:keys [:username :fileName]}]
	(
		workspaces/deleteFromPersonalWorkspace :username username :resourceClass "GDB_File" :resourceIDMap {"GDB_DisplayName" fileName}
	)
)

(defn deleteFileInstance
	"Delete a file instance"
	[fileName]
	(
		gneo/deleteDetachNodes 	:label "GDB_File" 
								:parameters {"GDB_DisplayName" fileName}
	)
)

(defn addFileToDB
	"Add a file to the database"
	[& {:keys[:fileSrcPath :author :memberOfWorkspace]}]
	(createFileInstance :fileSrcPath fileSrcPath :author author :memberOfWorkspace memberOfWorkspace)
	(copyFileToDir fileSrcPath (derivePath (digest/md5 (deriveFileName fileSrcPath))))
)

(defn removeFileFromDB
	"Delete a file from the database"
	[fileName]
	(deleteFileInstance fileName)
	(removeFilefromDirectory fileName)
)
	
(defn init
	[]
	(createFileClass)
)


;;;;Task left:determining the key(GDB_FileID) for files