(ns gnowdb.spec.files
  (:gen-class)
  (:require [gnowdb.neo4j.gneo :as gneo]
  			[digest :as digest]
  			[clojure.java.shell :refer [sh]]
  			[gnowdb.spec.workspaces :as workspaces]
  			[pantomime.mime :refer [mime-type-of]]
  			[pantomime.extract :as extract]
  )
)

(defn- createFileClass
	[] 
	(gneo/createClass :className "GDB_File" :classType "NODE" :isAbstract? false :subClassOf ["GDB_Node"] :properties {} :execute? true)
	(gneo/createAttributeType :_name "GDB_Path" :_datatype "java.lang.String" :subTypeOf [] :execute? true)
	(gneo/createAttributeType :_name "GDB_Extension" :_datatype "java.lang.String" :subTypeOf [] :execute? true)
	(gneo/createAttributeType :_name "GDB_Size" :_datatype "java.lang.Long" :subTypeOf [] :execute? true)
	(gneo/createAttributeType :_name "GDB_MimeType" :_datatype "java.lang.String" :subTypeOf [] :execute? true)
	(gneo/createAttributeType :_name "GDB_FileID" :_datatype "java.lang.String" :subTypeOf [] :execute? true)
	(gneo/addClassAT :_atname "GDB_Path" :className "GDB_File" :execute? true)
	(gneo/addClassAT :_atname "GDB_Extension" :className "GDB_File" :execute? true)
	(gneo/addClassAT :_atname "GDB_Size" :className "GDB_File" :execute? true)	
	(gneo/addClassAT :_atname "GDB_FileID" :className "GDB_File" :execute? true)
	(gneo/addClassAT :_atname "GDB_MimeType" :className "GDB_File" :execute? true)
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

(defn- getMetaData 
	[filePath]
	(extract/parse filePath)
)

(defn- deriveFileName
	[filePath]
	(subs filePath (inc (clojure.string/last-index-of filePath "/")))
)

(defn- createFileInstance 
	":fileSrcPath should include the filename as well e.g. src/gnowdb/core.clj
	 :author is the name of the user uploading file 
	 :memberOfWorkspace is a vector containing names of groupworkspaces which will
	  contain the file."
	[& {:keys[:fileSrcPath :author :memberOfWorkspace] :or {:author "ADMIN" :memberOfWorkspace []}}]
	(let [fileName (deriveFileName fileSrcPath)]
		(gneo/createNodeClassInstances :className "GDB_File" :nodeList 	[{
																			"GDB_DisplayName" fileName 
																			"GDB_Extension" (subs fileName (inc (clojure.string/last-index-of fileName ".")))
																			"GDB_Size" (.length (java.io.File. filePath))
																			"GDB_Path" filePath
																			"GDB_FileID" (digest/md5 fileName)
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
		(gneo/createRelationClassInstances :className "GDB_MemberOfWorkspace" :relList 	[{
																								:fromClassName "GDB_File"
																								:fromPropertyMap {"GDB_DisplayName" fileName}
																								:toClassName "GDB_PersonalWorkspace"
																								:toPropertyMap {"GDB_DisplayName" author}
																								:propertyMap {}
																						}]
		)
		(if (not (empty? memberOfWorkspace))
			(
				map (fn [groupName]
					(publishToGroup :username author :groupName groupName :resourceIDMap {"GDB_DisplayName" fileName} :resourceClass "GDB_File")
				 	)
				memberOfWorkspace
			)
		)
	)
)


