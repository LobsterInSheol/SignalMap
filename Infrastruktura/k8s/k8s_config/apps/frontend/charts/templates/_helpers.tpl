{{- define "frontend.name" -}}
frontend
{{- end -}}

{{- define "frontend.fullname" -}}
{{ include "frontend.name" . }}
{{- end -}}
