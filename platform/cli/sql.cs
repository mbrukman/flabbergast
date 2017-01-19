using System;
using System.Collections.Generic;
using System.Data;
using System.Data.Common;
using System.Threading;

namespace Flabbergast {
public class DbQuery : Computation {
    private delegate string NameChooser(DbDataReader rs, long it);
    private delegate void Retriever(DbDataReader rs, MutableFrame frame, TaskMaster task_master);
    private delegate object Unpacker(DbDataReader rs, int position, TaskMaster task_master);

    private static Retriever Bind(string name, int position, Unpacker unpacker) {
        return (rs, frame, task_master) => frame.Set(name, rs.IsDBNull(position) ? Unit.NULL : unpacker(rs, position, task_master));
    }

    static readonly Dictionary<System.Type, Unpacker> unpackers = new Dictionary<System.Type, Unpacker>();
    private static readonly bool debug = (Environment.GetEnvironmentVariable("FLABBERGAST_SQL") ?? "") == "debug";
    static DbQuery() {
        AddUnpacker((rs, position, task_master) => {
            var str = rs.GetString(position);
            return str == null ? null : new SimpleStringish(str);
        }, typeof(string));
        AddUnpacker((rs, position, task_master) => rs.GetInt64(position), typeof(byte), typeof(sbyte), typeof(short), typeof(ushort), typeof(int), typeof(uint), typeof(long), typeof(ulong));
        AddUnpacker((rs, position, task_master) => rs.GetDouble(position), typeof(float), typeof(double));
        AddUnpacker((rs, position, task_master) => rs.GetBoolean(position), typeof(bool));
        AddUnpacker((rs, position, task_master) => Time.BaseTime.MakeTime(rs.GetDateTime(position), task_master), typeof(DateTime));
        AddUnpacker((rs, position, task_master) => {
            var result = new byte[rs.GetBytes(position, 0, null, 0, 0)];
            rs.GetBytes(position, 0, result, 0, result.Length);
            return result;
        }, typeof(byte[]));
        AddUnpacker((rs, position, task_master) => {
            var result = rs.GetValue(position);
            if (result == null) {
                return Unit.NULL;
            }
            if (result is string) {
                return new SimpleStringish((string)result);
            }
            if (result is sbyte || result is short || result is ushort || result is int || result is uint || result is long || result is ulong) {
                return (long) result;
            }
            if (result is bool) {
                return result;
            }
            if (result is float || result is double) {
                return (double)result;
            }
            if (result is DateTime) {
                return Time.BaseTime.MakeTime(rs.GetDateTime(position), task_master);
            }
            if (result is byte[]) {
                return result;
            }
            return Unit.NULL;
        }, typeof(object));
    }

    static void AddUnpacker(Unpacker unpacker, params System.Type[] sql_types) {
        foreach (var sql_type in sql_types) {
            unpackers[sql_type] = unpacker;
        }
    }

    private readonly SourceReference source_ref;
    private readonly Context context;
    private readonly Frame self;
    private InterlockedLookup interlock;
    private DbConnection connection = null;
    private string query = null;
    private Template row_tmpl;

    public DbQuery(TaskMaster task_master, SourceReference source_ref,
                   Context context, Frame self, Frame container) : base(task_master) {
        this.source_ref = source_ref;
        this.context = context;
        this.self = self;
    }

    protected override void Run() {
        if (interlock == null) {
            interlock = new InterlockedLookup(this, task_master, source_ref, context);
            interlock.LookupMarshalled<DbConnection>("Expected “{0}” to come from “sql:” import.", x => connection = x,  "connection");
            interlock.LookupStr(x => query = x, "sql_query");
            interlock.Lookup<Template>(x => row_tmpl = x, "sql_row_tmpl");
        }
        if (!interlock.Away()) return;
        try {
            var command = connection.CreateCommand();
            command.CommandType = System.Data.CommandType.Text;
            command.CommandText = query;
            var reader = command.ExecuteReader();
            if (debug) {
                Console.WriteLine("SQL Query to {0}: {1}", connection, query);
            }
            NameChooser name_chooser = (rs, it) => SupportFunctions.OrdinalNameStr(it);
            var retrievers = new List<Retriever>();
            for (int col = 0; col < reader.FieldCount; col++) {
                var column = col;
                if (reader.GetName(col) == "ATTRNAME") {
                    name_chooser = (rs, it) => rs.GetString(column);
                    continue;
                }
                if (reader.GetName(col).StartsWith("$")) {
                    var attr_name = reader.GetName(col).Substring(1);
                    if (!task_master.VerifySymbol(source_ref, attr_name)) {
                        return;
                    }
                    retrievers.Add((rs, frame, _task_master) => frame.Set(attr_name, rs.IsDBNull(column) ? Precomputation.Capture(Unit.NULL) : Lookup.Do(rs.GetString(column).Split('.'))));
                    continue;
                }
                Unpacker unpacker;
                if (!unpackers.TryGetValue(reader.GetFieldType(col), out unpacker)) {
                    task_master
                    .ReportOtherError(
                        source_ref,
                        string.Format(
                            "Cannot convert SQL type “{0}” for column “{1}” into Flabbergast type.",
                            reader.GetFieldType(col),
                            reader.GetName(col)));
                }
                if (!task_master.VerifySymbol(source_ref,
                                              reader.GetName(col))) {
                    return;
                }
                retrievers.Add(Bind(reader.GetName(col), col, unpacker));
            }

            var list = new MutableFrame(task_master, source_ref, context, self);
            for (int it = 1; reader.Read(); it++) {
                var frame = new MutableFrame(task_master, new JunctionReference(string.Format("SQL template instantiation row {0}", it), "<sql>", 0, 0, 0, 0, source_ref, row_tmpl.SourceReference), Context.Append(list.Context, row_tmpl.Context), list);
                foreach (var r in retrievers) {
                    r(reader, frame, task_master);
                }
                foreach (var name in row_tmpl.GetAttributeNames()) {
                    if (!frame.Has(name)) {
                        frame.Set(name, row_tmpl[name]);
                    }
                }
                list.Set(name_chooser(reader, it), frame);
            }
            list.Slot();
            result = list;
            return;
        } catch (DataException e) {
            task_master.ReportOtherError(source_ref, e.Message);
        } catch (DbException e) {
            task_master.ReportOtherError(source_ref, e.Message);
            return;
        }
    }
}

public class DbUriHandler : UriHandler {

    private static readonly Dictionary<string, Func<DbConnection, object>> connection_hooks = new Dictionary<string, Func<DbConnection, object>> {
        {"database",  c => c.Database},
        {"product_name",  c => c.DataSource},
        {"product_version",  c => c.ServerVersion},
        {"driver_name",  c => c.GetType().Name},
        {"driver_version",  c => c.GetType().Assembly.GetName().Version.ToString()},
        {"platform",  c => "ADO.NET"}
    };

    internal delegate void HandleParams(string host, string port, string database);

    internal static bool ParseUri(String uri_fragment, DbConnectionStringBuilder builder, string host_param, string port_param, string user_param, string password_param, string database_param, out string err) {
        return ParseUri(uri_fragment, builder, user_param, password_param, (host, port, database) => {
            builder[host_param] = host;
            if (port != null) {
                builder[port_param] = port;
            }
            builder[database_param] = database;
        }, out err);
    }
    internal static bool ParseUri(String uri_fragment, DbConnectionStringBuilder builder, string user_param, string password_param, HandleParams handle_params, out string err) {
        err = null;
        int host_start = 0;
        int user_end = 0;
        while (user_end < uri_fragment.Length && (Char.IsLetterOrDigit(uri_fragment[user_end]) || uri_fragment[user_end] == '_')) user_end++;
        if (user_end == uri_fragment.Length) {
            // We know this is malformed.
            err = "Missing “/” followed by database in SQL URI.";
            return false;
        } else {
            switch (uri_fragment[user_end]) {
            case '@':
                // End of user string.
                builder[user_param] = uri_fragment.Substring(0, user_end);
                host_start = user_end + 1;
                break;
            case ':':
                // Possible password. Might be port.
                int password_end = user_end + 1;
                while (password_end < uri_fragment.Length && "/@".IndexOf(uri_fragment[password_end]) == -1) password_end++;
                if (password_end == uri_fragment.Length) {
                    // We know this is malformed.
                    err = "Missing “/” followed by database in SQL URI.";
                    return false;
                } else if (uri_fragment[password_end] == '@') {
                    host_start = password_end + 1;
                    builder[user_param] = uri_fragment.Substring(0, user_end);
                    builder[password_param] = uri_fragment.Substring(user_end + 1, password_end - user_end - 1);
                }
                // Else, this is really the host:port.
                break;
            default:
                // This is really the host.
                break;
            }
        }
        int host_end = host_start;
        int db_start;
        while (host_end < uri_fragment.Length && "/:".IndexOf(uri_fragment[host_end]) == -1) {
            // IPv6 address?
            if (uri_fragment[host_end] == '[') {
                while (host_end < uri_fragment.Length && uri_fragment[host_end] != ']') host_end++;
            }
            host_end++;
        }
        if (host_end >= uri_fragment.Length) {
            err = "Missing “/” followed by database in SQL URI.";
            return false;
        }
        string port = null;
        if (uri_fragment[host_end] == ':') {
            int port_start = host_end + 1;
            int port_end = port_start;
            while (port_end < uri_fragment.Length && Char.IsDigit(uri_fragment[port_end])) port_end++;
            if (port_end == uri_fragment.Length) {
                err = "Missing “/” followed by database in SQL URI.";
                return false;
            }
            if (uri_fragment[port_end] != '/') {
                err = "Non-numeric data in port in SQL URI.";
                return false;
            }
            port = uri_fragment.Substring(port_start, port_end - port_start);
            db_start = port_end + 1;
        } else if (uri_fragment[host_end] == '/') {
            db_start = host_end + 1;
        } else {
            err = "Junk after host in SQL URI.";
            return false;
        }

        int db_end = db_start;
        while (db_end < uri_fragment.Length && uri_fragment[db_end] != '/') db_end++;
        if (db_end < uri_fragment.Length) {
            err = "Junk after database in SQL URI.";
            return false;
        }
        handle_params(uri_fragment.Substring(host_start, host_end - host_start), port, uri_fragment.Substring(db_start));
        return true;
    }

    public DbUriHandler() {
    }

    public ResourcePathFinder Finder {
        get;
        set;
    }

    public string UriName {
        get {
            return "ADO.NET gateway";
        }
    }
    public Computation ResolveUri(TaskMaster task_master, string uri, out LibraryFailure reason) {
        if (!uri.StartsWith("sql:")) {
            reason = LibraryFailure.Missing;
            return null;
        }
        reason = LibraryFailure.None;
        try {
            var param = new Dictionary<string, string>();

            int first_colon = 5;
            while (first_colon < uri.Length && uri[first_colon] != ':') first_colon++;
            if (first_colon >= uri.Length) {
                return new FailureComputation(task_master, new ClrSourceReference(), "Bad provider in URI “" + uri + "”.");
            }
            var provider = uri.Substring(4, first_colon - 4);
            int question_mark = first_colon;
            while (question_mark < uri.Length && uri[question_mark] != '?') question_mark++;
            var uri_fragment = uri.Substring(first_colon + 1, question_mark - first_colon - 1);
            if (question_mark < uri.Length - 1) {
                foreach (var param_str in uri.Substring(question_mark + 1).Split(new [] {'&'})) {
                    if (param_str.Length == 0)
                        continue;
                    var parts = param_str.Split(new [] {'='}, 2);
                    if (parts.Length != 2) {
                        return new FailureComputation(task_master, new ClrSourceReference(), "Bad parameter “" + param_str + "”.");
                    }
                    param[parts[0]] = parts[1];
                }
            }

            string error;
            var connection = DbParser.Parse(provider, uri_fragment, param, Finder, out error);
            if (connection == null) {
                return new FailureComputation(task_master, new ClrSourceReference(), error ?? "Bad URI.");
            }

            connection.Open();

            var connection_proxy = ReflectedFrame.Create(task_master, connection, connection_hooks);
            var plus_position = provider.IndexOf('+');
            connection_proxy.Set("provider", new SimpleStringish(plus_position == -1 ? provider : provider.Substring(0, plus_position)));
            return new Precomputation(connection_proxy);
        } catch (Exception e) {
            return new FailureComputation(task_master, new ClrSourceReference(e), e.Message);
        }
    }
}
}
