/**
 * Writing reports to files. A module that turns what it reads into a CSV file uses {@link CsvReport}; the file is
 * written with plain {@code java.nio.file}, and a failure is reported as the checked {@link ReportExportException}.
 */
@NamedInterface("reports")
package com.kibootia.backend.platform.reports;

import org.springframework.modulith.NamedInterface;
