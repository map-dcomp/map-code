#BBN_LICENSE_START -- DO NOT MODIFY BETWEEN LICENSE_{START,END} Lines
# Copyright (c) <2017,2018,2019,2020>, <Raytheon BBN Technologies>
# To be applied to the DCOMP/MAP Public Source Code Release dated 2018-04-19, with
# the exception of the dcop implementation identified below (see notes).
# 
# Dispersed Computing (DCOMP)
# Mission-oriented Adaptive Placement of Task and Data (MAP) 
# 
# All rights reserved.
# 
# Redistribution and use in source and binary forms, with or without
# modification, are permitted provided that the following conditions are met:
# 
# Redistributions of source code must retain the above copyright
# notice, this list of conditions and the following disclaimer.
# Redistributions in binary form must reproduce the above copyright
# notice, this list of conditions and the following disclaimer in the
# documentation and/or other materials provided with the distribution.
# 
# THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
# IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
# TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
# PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
# HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
# SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED
# TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
# PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
# LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
# NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
# SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
#BBN_LICENSE_END
import warnings
with warnings.catch_warnings():
    import numpy as np
    import datetime
    import re
    import logging
    import os
    import os.path
    import json

def timestamp_to_minutes(timestamp):
    """
    Convert a Java timestamp to fractional minutes
    
    Args:
        timestamp (int): Java timestamp

    Return:
        float: fractional minutes
    """
    return timestamp_to_seconds(timestamp) / 60.0


def timestamp_to_seconds(timestamp):
    """
    Convert a Java timestamp to fractional seconds
    
    Args:
        timestamp (int): Java timestamp

    Return:
        float: fractional seconds
    """
    return float(timestamp) / 1000.0


def find_ncp_folder(node_base):
    """
    Find the folder under node_base that contains events.csv and the timestamped folders.
    This may be node_base or a directory under it.
    This method exists to handle the differences in the directory structure between lo-fi and hi-fi.

    Args:
        node_base (Path): where to look

    Return:
        Path: The path to use or None if not found
    """
    lofi_events = node_base / 'events.csv'
    if lofi_events.exists():
        return node_base
    agent_dir = node_base / 'agent'
    if not agent_dir.is_dir():
        return None
    for node_name_dir in agent_dir.iterdir():
        if node_name_dir.is_dir():
            check = node_name_dir / 'agent-configuration.json'
            if check.exists():
                return node_name_dir
    return None


def fill_missing_times(all_times, time_data):
    """
    Create a numpy array that contains values for all_times based on time_data.
    Any times before the first time in time_data are filled with zero.
    Any times after the last time in time_data are filled with zero.
    Any times in between are filled with the value to the left.

    Args:
    time_data (dict): time to value
    all_times (list): All times that need values

    Returns:
    numpy.array: filled values
    """

    # make a copy so that the original value isn't modified 
    modified_time_data = time_data.copy()

    # Add NaN values for all missing times
    for time in all_times:
        if time not in modified_time_data:
            modified_time_data[time] = None

    times, values = zip(*sorted(modified_time_data.items()))

    # fill the values with the value to the left
    values_ar = np.asarray(values, dtype=np.float32)
    indicies = np.arange(len(values_ar))

    # make all nans on the right be 0
    max_non_nan_idx = max(np.where(np.isnan(values_ar), 0, indicies))
    np.put(values_ar, np.arange(max_non_nan_idx+1, len(values_ar)), 0)

    mask = np.isnan(values_ar)
    non_nan_idx = np.where(~mask, indicies, 0)

    # use left most non-nan value
    values_ar = values_ar[np.maximum.accumulate(non_nan_idx)]

    # replace nans on left end with 0
    values_ar = np.nan_to_num(values_ar)

    return values_ar


def log_line_to_time(line, time_zone):
    """
    Arguments:
        line (str): line to parse the timestamp on
        time_zone (timezone): If not None, then used to determine the timzone of the log line

    Returns:
        datetime: the datetime or None if one cannot be found in the line
    """
    bracket_index = line.find(" [")
    if -1 == bracket_index:
        return None
    
    log_file_reference_time = line[0:bracket_index]

    try:
        if time_zone is None:
                # if a time zone was not given, look for time zone information in the log message timestamp
                time = datetime.datetime.strptime(log_file_reference_time, '%Y-%m-%d/%H:%M:%S.%f/%z')
        else:
            # if a time zone is given, use the old log timestamp format and the given time zone
            time = datetime.datetime.strptime(log_file_reference_time, '%Y-%m-%d %H:%M:%S,%f')
            time = time_zone.localize(time)
        return time;
    except ValueError:
        return None


def datetime_to_string(date_time):
    """
    Output a datetime in our preferred format

    Arguments:
        date_time (datetime): the datetime to convert

    Returns:
        str: string representation of the datetime
    """

    if date_time is None:
        return "None"
    else:
        return date_time.strftime('%Y-%m-%d/%H:%M:%S.%f/%z')

def node_name_from_dir(dir):
    """
    Determine the name of a node given a path. The last path element
    is assumed to be either the node name or <node name>.map.dcomp.
    
    Arguments:
        dir (Path): path to convert to a node name
    Returns:
        str: the name of the node
    """
    match = re.match(r'^(.*)\.map\.dcomp$', dir.name)
    if not match:
        return dir.name
    else:
        return match.group(1)

        
def setup_logging(
    default_path='logging.json',
    default_level=logging.INFO,
    env_key='LOG_CFG'
):
    """
    Setup logging configuration
    """
    path = default_path
    value = os.getenv(env_key, None)
    if value:
        path = value
    if os.path.exists(path):
        with open(path, 'r') as f:
            config = json.load(f)
        logging.config.dictConfig(config)
    else:
        logging.basicConfig(level=default_level)


def skip_null_lines(f):
    """
    Arguments:
        f(file handle): the file to read a line at a time
    Returns:
        iterator<str>: the lines that do not contain null characters
    """
    for line in f:
        if not re.search('\0', line):
            yield line
            
       
def set_figure_size(fig):
    """
    Used to set the size of all graphs to the same size

    Arguments:
        fig(Figure): matplotlib figure to set the size on
    """
    
    fig.set_size_inches(10, 6)
    
