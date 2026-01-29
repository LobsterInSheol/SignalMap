{{- define "backend.name" -}}
backend
{{- end -}}

{{- define "backend.fullname" -}}
{{ include "backend.name" . }}
{{- end -}}
