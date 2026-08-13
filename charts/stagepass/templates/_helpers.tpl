{{/*
릴리스 이름 기반 공통 이름. 릴리스명이 차트명을 이미 포함하면 중복을 피합니다.
*/}}
{{- define "stagepass.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{- define "stagepass.fullname" -}}
{{- if .Values.fullnameOverride -}}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" -}}
{{- else -}}
{{- $name := default .Chart.Name .Values.nameOverride -}}
{{- if contains $name .Release.Name -}}
{{- .Release.Name | trunc 63 | trimSuffix "-" -}}
{{- else -}}
{{- printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" -}}
{{- end -}}
{{- end -}}
{{- end -}}

{{/*
모든 리소스에 붙는 공통 라벨.
app.kubernetes.io/version 이 배포된 이미지 태그를 드러내므로
`kubectl get deploy -L app.kubernetes.io/version` 으로 배포 버전을 바로 확인할 수 있습니다.
*/}}
{{- define "stagepass.labels" -}}
helm.sh/chart: {{ printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" }}
app.kubernetes.io/name: {{ include "stagepass.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/version: {{ include "stagepass.imageTag" . | quote }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
app.kubernetes.io/part-of: stagepass
{{- end -}}

{{/*
selector 는 불변 필드이므로 version 같은 변하는 값을 넣으면 안 됩니다.

이 릴리스에 속한 모든 워크로드(앱 + 의존 서비스)가 공유하는 라벨입니다.
★ 이것만으로 Service selector 를 만들면 안 됩니다 — postgres/redis/kafka 까지 전부 매칭됩니다.
  워크로드를 가리킬 때는 반드시 component 를 더한 아래 셀렉터를 쓰세요.
*/}}
{{- define "stagepass.selectorLabels" -}}
app.kubernetes.io/name: {{ include "stagepass.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end -}}

{{/*
api 워크로드만 가리키는 셀렉터.
*/}}
{{- define "stagepass.apiSelectorLabels" -}}
{{ include "stagepass.selectorLabels" . }}
app.kubernetes.io/component: api
{{- end -}}

{{/*
image.tag 가 비어 있으면 Chart.yaml 의 appVersion 으로 대체합니다.
*/}}
{{- define "stagepass.imageTag" -}}
{{- default .Chart.AppVersion .Values.image.tag -}}
{{- end -}}

{{- define "stagepass.image" -}}
{{- printf "%s:%s" .Values.image.repository (include "stagepass.imageTag" .) -}}
{{- end -}}

{{/*
Secret 이름 — 앱과 postgres 가 같은 db-password 를 참조합니다.
*/}}
{{- define "stagepass.secretName" -}}
{{- printf "%s-secret" (include "stagepass.fullname" .) -}}
{{- end -}}
