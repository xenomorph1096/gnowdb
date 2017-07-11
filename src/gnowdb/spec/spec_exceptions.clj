(ns gnowdb.spec.spec_exceptions
  (:gen-class)
)

(defn inputException
	[message]
	(throw 
		(ex-info (str "InputException:" message) {})
	)
)

(defn workspaceNameException
	[message]
	(throw
		(ex-info (str "WorkspaceNameException:" message) {})
	)
)

(defn sizeLimitExceededException
	[message]
	(throw
		(ex-info (str "SizeLimitExceededException:" message) {})
	)
)

(defn crossPublicationException
	[message]
	(throw
		(ex-info (str "CrossPublicationException:" message) {})
	)
)

(defn userException
	[message]
	(throw
		(ex-info (str "UserException:" message) {})
	)
)

(defn keysException
	[message]
	(throw
		(ex-info (str "KeysException:" message) {})
	)
)

(defn namingException
	[message]
	(throw
		(ex-info (str "NamingException:" message) {})
	)
)

(defn throwException
	[exceptionFunctionName message]
	(eval (read-string (str "(" exceptionFunctionName " " message ")")))
)

