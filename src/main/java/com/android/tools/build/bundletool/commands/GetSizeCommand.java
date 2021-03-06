/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */

package com.android.tools.build.bundletool.commands;

import static com.android.tools.build.bundletool.utils.files.FilePreconditions.checkFileExistsAndReadable;

import com.android.bundle.Devices.DeviceSpec;
import com.android.tools.build.bundletool.commands.CommandHelp.CommandDescription;
import com.android.tools.build.bundletool.commands.CommandHelp.FlagDescription;
import com.android.tools.build.bundletool.device.DeviceSpecParser;
import com.android.tools.build.bundletool.utils.files.FilePreconditions;
import com.android.tools.build.bundletool.utils.flags.Flag;
import com.android.tools.build.bundletool.utils.flags.ParsedFlags;
import com.google.auto.value.AutoValue;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableSet;
import java.nio.file.Path;
import java.util.Optional;

/** Gets over-the-wire sizes of APKS that are going to be served from the APK Set. */
@AutoValue
public abstract class GetSizeCommand {

  public static final String COMMAND_NAME = "get-size";

  /** Dimensions to expand the sizes in the output against. */
  public enum Dimension {
    SDK,
    ABI,
    LANGUAGE,
    SCREEN_DENSITY,
    ALL
  }

  private static final Flag<Path> APKS_ARCHIVE_FILE_FLAG = Flag.path("apks");
  private static final Flag<Path> DEVICE_SPEC_FLAG = Flag.path("device-spec");
  private static final Flag<ImmutableSet<String>> MODULES_FLAG = Flag.stringSet("modules");
  private static final Flag<Boolean> INSTANT_FLAG = Flag.booleanFlag("instant");
  private static final Flag<ImmutableSet<Dimension>> DIMENSIONS_FLAG =
      Flag.enumSet("dimensions", Dimension.class);
  private static final Joiner COMMA_JOINER = Joiner.on(',');

  @VisibleForTesting
  static final ImmutableSet<Dimension> SUPPORTED_DIMENSIONS =
      ImmutableSet.of(Dimension.SDK, Dimension.ABI, Dimension.LANGUAGE, Dimension.SCREEN_DENSITY);

  public abstract Path getApksArchivePath();

  public abstract DeviceSpec getDeviceSpec();

  public abstract Optional<ImmutableSet<String>> getModules();

  public abstract ImmutableSet<Dimension> getDimensions();

  /** Gets whether instant APKs should be used for size calculation. */
  public abstract boolean getInstant();

  public static Builder builder() {
    return new AutoValue_GetSizeCommand.Builder()
        .setDeviceSpec(DeviceSpec.getDefaultInstance())
        .setInstant(false)
        .setDimensions(ImmutableSet.of());
  }

  /** Builder for the {@link GetSizeCommand}. */
  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setApksArchivePath(Path apksArchivePath);

    public abstract Builder setDeviceSpec(DeviceSpec deviceSpec);

    public abstract Builder setModules(ImmutableSet<String> modules);

    public abstract Builder setDimensions(ImmutableSet<Dimension> dimensions);

    /**
     * Sets whether only instant APKs should be used in size calculation.
     *
     * <p>The default is {@code false}. If this is set to {@code true}, the instant APKs will be
     * used for calculating size instead of installable APKs.
     */
    public abstract Builder setInstant(boolean instant);

    public abstract GetSizeCommand build();
  }

  public static GetSizeCommand fromFlags(ParsedFlags flags) {
    Path apksArchivePath = APKS_ARCHIVE_FILE_FLAG.getRequiredValue(flags);
    Optional<Path> deviceSpecPath = DEVICE_SPEC_FLAG.getValue(flags);
    Optional<ImmutableSet<String>> modules = MODULES_FLAG.getValue(flags);
    Optional<Boolean> instant = INSTANT_FLAG.getValue(flags);

    ImmutableSet<Dimension> dimensions = DIMENSIONS_FLAG.getValue(flags).orElse(ImmutableSet.of());
    flags.checkNoUnknownFlags();

    checkFileExistsAndReadable(apksArchivePath);
    deviceSpecPath.ifPresent(FilePreconditions::checkFileExistsAndReadable);
    DeviceSpec deviceSpec =
        deviceSpecPath
            .map(DeviceSpecParser::parsePartialDeviceSpec)
            .orElse(DeviceSpec.getDefaultInstance());

    GetSizeCommand.Builder command =
        builder().setApksArchivePath(apksArchivePath).setDeviceSpec(deviceSpec);

    modules.ifPresent(command::setModules);

    instant.ifPresent(command::setInstant);

    if (dimensions.contains(Dimension.ALL)) {
      dimensions = SUPPORTED_DIMENSIONS;
    }

    command.setDimensions(dimensions);

    return command.build();
  }

  public static CommandHelp help() {
    return CommandHelp.builder()
        .setCommandName(COMMAND_NAME)
        .setCommandDescription(
            CommandDescription.builder()
                .setShortDescription(
                    "Gets the over-the-wire sizes (sorted desc) of APKs served to different "
                        + "devices configurations from an APK Set.")
                .build())
        .addFlag(
            FlagDescription.builder()
                .setFlagName(APKS_ARCHIVE_FILE_FLAG.getName())
                .setExampleValue("archive.apks")
                .setDescription(
                    "Path to the archive file generated by the '%s' command.",
                    BuildApksCommand.COMMAND_NAME)
                .build())
        .addFlag(
            FlagDescription.builder()
                .setFlagName(DEVICE_SPEC_FLAG.getName())
                .setExampleValue("device-spec.json")
                .setOptional(true)
                .setDescription(
                    "Path to the device spec file to be used for matching (defaults to empty "
                        + "device spec). Note that partial specifications are allowed in the "
                        + "file as opposed to the spec generated by '%s'.",
                    GetDeviceSpecCommand.COMMAND_NAME)
                .build())
        .addFlag(
            FlagDescription.builder()
                .setFlagName(DIMENSIONS_FLAG.getName())
                .setExampleValue(COMMA_JOINER.join(Dimension.values()))
                .setOptional(true)
                .setDescription(
                    "Specifies which dimensions to expand the sizes in the output "
                        + "against. Note that ALL is a shortcut to all other dimensions and "
                        + "including ALL here would cause the output to be expanded over "
                        + "all possible dimensions.")
                .build())
        .addFlag(
            FlagDescription.builder()
                .setFlagName(MODULES_FLAG.getName())
                .setExampleValue("base,module1,module2")
                .setOptional(true)
                .setDescription(
                    "List of modules to run this report on (defaults to all the modules installed "
                        + "during the first download). Note that the dependent modules will also "
                        + "be considered. We than ignore standalone APKs for size calculation when "
                        + "this flag is set.")
                .build())
        .addFlag(
            FlagDescription.builder()
                .setFlagName(INSTANT_FLAG.getName())
                .setOptional(true)
                .setDescription(
                    "When set, APKs of the instant modules will be considered instead of the "
                        + "installable APKs. Defaults to false.")
                .build())
        .build();
  }

  // Don't subclass outside the package. Hide the implicit constructor from IDEs/docs.
  GetSizeCommand() {}
}
