<#
Sends synthetic error logs to Kafka so you can measure how many LLM calls error grouping saves.
Each message gets a unique traceId plus random numbers / UUIDs / timestamps, so only the
error *pattern* repeats. All data is fabricated.

Usage (from the ai-rca-platform folder, with docker compose and the app running):
  .\scripts\send-sample-errors.ps1            # 500 messages
  .\scripts\send-sample-errors.ps1 -Count 50
Then open http://localhost:8080/stats
#>
param(
    [int]$Count = 500
)

$templates = @(
    @{ service = 'account-service'; exception = 'SerializationException'
       message = 'Failed to deserialize AccountCreated event for account {n}. Expected magic byte 0x00 but found 0x7b. Schema registry mismatch ID {n}.' },
    @{ service = 'signup-service'; exception = 'TimeoutException'
       message = 'Call to account-service timed out after {n} ms for request {uuid}' },
    @{ service = 'kyc-service'; exception = 'ConnectException'
       message = 'Connection refused to kyc-provider at 10.0.{n}.{n}:8443 after {n} attempts' },
    @{ service = 'api-gateway'; exception = 'HttpServerErrorException'
       message = 'Upstream signup-service returned 503 for request {uuid} at {ts}' },
    @{ service = 'account-service'; exception = 'DataIntegrityViolationException'
       message = 'duplicate key value violates unique constraint accounts_ref_key for account {n}' },
    @{ service = 'signup-service'; exception = 'NullPointerException'
       message = 'Cannot invoke String.length() because field is null for signup {uuid}' },
    @{ service = 'kyc-service'; exception = 'SocketTimeoutException'
       message = 'Read timed out after {n} ms while verifying document {uuid}' },
    @{ service = 'account-service'; exception = 'PSQLException'
       message = 'FATAL: remaining connection slots are reserved, active connections {n}' }
)

$random = [System.Random]::new()

function Fill([string]$text) {
    $text = [regex]::Replace($text, '\{n\}', { param($m) $random.Next(1, 255).ToString() })
    $text = [regex]::Replace($text, '\{uuid\}', { param($m) [guid]::NewGuid().ToString() })
    $text = [regex]::Replace($text, '\{ts\}', { param($m) (Get-Date).AddSeconds(-$random.Next(0, 3600)).ToString('yyyy-MM-ddTHH:mm:ss') })
    return $text
}

$lines = for ($i = 1; $i -le $Count; $i++) {
    $t = $templates[$random.Next(0, $templates.Count)]
    [ordered]@{
        service   = $t.service
        timestamp = (Get-Date).ToString('yyyy-MM-ddTHH:mm:ss')
        traceId   = "load-$([guid]::NewGuid())"
        exception = $t.exception
        message   = Fill $t.message
    } | ConvertTo-Json -Compress
}

Write-Host "Sending $Count messages built from $($templates.Count) error patterns..."
$lines | docker exec -i rca-kafka kafka-console-producer --bootstrap-server localhost:9092 --topic app.telemetry.raw
if ($LASTEXITCODE -ne 0) { throw "kafka-console-producer failed (is 'docker compose up' running?)" }
Write-Host "Done. Wait for the analyses to finish, then open http://localhost:8080/stats"
