param(
    [string]$SourceDir = ".\data\Data",
    [string]$OutputDir = ".\dataset_standardized"
)

$imagesSrc = Join-Path $SourceDir "images"
$labelsSrc = Join-Path $SourceDir "labels"

$imagesOut = Join-Path $OutputDir "images"
$labelsOut = Join-Path $OutputDir "labels"

New-Item -ItemType Directory -Path $imagesOut -Force | Out-Null
New-Item -ItemType Directory -Path $labelsOut -Force | Out-Null

$stats = @{
    total = 0
    bboxConverted = 0
    polygonKept = 0
    emptySkipped = 0
    multiObject = 0
    errors = 0
}

function Clamp-Double {
    param([double]$val)
    $d0 = [double]0
    $d1 = [double]1
    if ($val -lt $d0) { return $d0 }
    if ($val -gt $d1) { return $d1 }
    return $val
}

function Convert-BBoxToPolygon {
    param([double]$xc, [double]$yc, [double]$w, [double]$h, [string]$classId)
    $x1 = Clamp-Double ($xc - $w / 2)
    $y1 = Clamp-Double ($yc - $h / 2)
    $x2 = Clamp-Double ($xc + $w / 2)
    $y2 = Clamp-Double ($yc - $h / 2)
    $x3 = Clamp-Double ($xc + $w / 2)
    $y3 = Clamp-Double ($yc + $h / 2)
    $x4 = Clamp-Double ($xc - $w / 2)
    $y4 = Clamp-Double ($yc + $h / 2)
    $fmt = "{0:G15}"
    return "$classId $($fmt -f $x1) $($fmt -f $y1) $($fmt -f $x2) $($fmt -f $y2) $($fmt -f $x3) $($fmt -f $y3) $($fmt -f $x4) $($fmt -f $y4)"
}

$labelFiles = Get-ChildItem -Path $labelsSrc -Filter "*.txt"
$totalFiles = $labelFiles.Count
$currentFile = 0

foreach ($labelFile in $labelFiles) {
    $currentFile++
    if ($currentFile % 1000 -eq 0) {
        Write-Host "Processing $currentFile / $totalFiles..."
    }

    $baseName = $labelFile.BaseName

    $jpgFile = Get-ChildItem -Path $imagesSrc -Filter "$baseName.*" -ErrorAction SilentlyContinue | Select-Object -First 1
    if (-not $jpgFile) {
        $stats.errors++
        continue
    }
    $imagePath = $jpgFile.FullName

    $rawContent = Get-Content $labelFile.FullName -Raw -ErrorAction SilentlyContinue
    if ($null -eq $rawContent) {
        $stats.emptySkipped++
        continue
    }
    $content = $rawContent.Trim()
    if ([string]::IsNullOrWhiteSpace($content)) {
        $stats.emptySkipped++
        continue
    }

    $lines = $content -split "`n" | Where-Object { $_.Trim() -ne "" }
    $newLines = @()

    foreach ($line in $lines) {
        $line = $line.Trim()
        $parts = $line -split '\s+'
        $numValues = $parts.Count

        if ($numValues -eq 1) {
            continue
        }

        $classId = $parts[0]

        if ($numValues -eq 5) {
            $xc = [double]::Parse($parts[1], [System.Globalization.CultureInfo]::InvariantCulture)
            $yc = [double]::Parse($parts[2], [System.Globalization.CultureInfo]::InvariantCulture)
            $w = [double]::Parse($parts[3], [System.Globalization.CultureInfo]::InvariantCulture)
            $h = [double]::Parse($parts[4], [System.Globalization.CultureInfo]::InvariantCulture)
            $polygonLine = Convert-BBoxToPolygon -xc $xc -yc $yc -w $w -h $h -classId $classId
            $newLines += $polygonLine
            $stats.bboxConverted++
        }
        elseif (($numValues - 1) % 2 -eq 0 -and $numValues -ge 7) {
            $fmt = "{0:G15}"
            $coordParts = @()
            for ($i = 1; $i -lt $numValues; $i++) {
                $val = [double]::Parse($parts[$i], [System.Globalization.CultureInfo]::InvariantCulture)
                $val = Clamp-Double $val
                $coordParts += ($fmt -f $val)
            }
            $newLine = "$classId " + ($coordParts -join " ")
            $newLines += $newLine
            $stats.polygonKept++
        }
        elseif ($numValues -eq 9) {
            $newLines += $line
            $stats.polygonKept++
        }
        else {
            $newLines += $line
            $stats.polygonKept++
        }
    }

    if ($newLines.Count -gt 0) {
        $newContent = $newLines -join "`n"
        Set-Content -Path (Join-Path $labelsOut "$baseName.txt") -Value $newContent -NoNewline
        Copy-Item -Path $imagePath -Destination (Join-Path $imagesOut "$baseName$($jpgFile.Extension)") -Force
        $stats.total++
        if ($lines.Count -gt 1) {
            $stats.multiObject++
        }
    }
}

Write-Host "`n=== ESTADISTICAS ==="
Write-Host "Archivos procesados: $($stats.total)"
Write-Host "BBox convertidos a poligono: $($stats.bboxConverted)"
Write-Host "Poligonos mantenidos: $($stats.polygonKept)"
Write-Host "Archivos vacios saltados: $($stats.emptySkipped)"
Write-Host "Archivos multi-objeto: $($stats.multiObject)"
Write-Host "Errores: $($stats.errors)"
Write-Host "`nDataset estandarizado en: $OutputDir"
