using System.Collections.Generic;

namespace Flabbergast
{
    /**
     * Do lookup by creating a grid of contexts where the value might reside and all the needed names.
     */
    public class Lookup : Future
    {
        private readonly Frame[] frames;

        /**
        * The name components in the lookup expression.
        */
        private readonly string[] names;

        /**
        * The current context in the grid being considered.
        */
        private int frame_index;

        private readonly LinkedList<Attempt> known_attempts = new LinkedList<Attempt>();

        public Lookup(TaskMaster task_master, SourceReference source_ref, string[] names, Context context) : base(
            task_master)
        {
            SourceReference = source_ref;
            this.names = names;

            /* Create  grid where the first entry is the frame under consideration. */
            frames = new Frame[context.Length];
            var index = 0;
            foreach (var frame in context.Fill())
                frames[index++] = frame;
        }

        public int FrameCount => frames.Length;

        public Frame this[int name, int frame]
        {
            get
            {
                foreach (var current in known_attempts)
                {
                    if (current.frame == frame && current.name > name
                        || current.frame > frame)
                        return null;
                    if (current.frame == frame && current.name == name)
                        return current.source_frame;
                }
                return null;
            }
        }

        public Frame LastFrame => known_attempts.Last.Value.source_frame;

        public string LastName => names[known_attempts.Last.Value.name];

        public string Name => string.Join(".", names);

        public int NameCount => names.Length;

        public SourceReference SourceReference { get; }

        public static ComputeValue Do(params string[] names)
        {
            return (task_master, reference, context, self, container) =>
            {
                if (names.Length == 0)
                    return new FailureFuture(task_master, reference, "Missing names in lookup.");
                foreach (var name in names)
                    if (!task_master.VerifySymbol(reference, name))
                        return BlackholeFuture.INSTANCE;
                return new Lookup(task_master, reference, names, context);
            };
        }

        public string GetName(int index)
        {
            return names[index];
        }

        protected override void Run()
        {
            while (frame_index < frames.Length)
            {
                var index = frame_index++;
                var root_attempt = new Attempt(this, 0, index, frames[index]);
                known_attempts.AddLast(root_attempt);
                if (frames[index].GetOrSubscribe(names[0], root_attempt.Consume))
                    return;
            }
            task_master.ReportLookupError(this, null);
        }

        private class Attempt
        {
            public readonly int frame;
            public readonly int name;
            private readonly Lookup owner;
            public Frame result_frame;
            public readonly Frame source_frame;

            public Attempt(Lookup owner, int name, int frame, Frame source_frame)
            {
                this.owner = owner;
                this.name = name;
                this.frame = frame;
                this.source_frame = source_frame;
            }

            public void Consume(object return_value)
            {
                if (name == owner.names.Length - 1)
                {
                    owner.result = return_value;
                    owner.WakeupListeners();
                }
                else if (return_value is Frame)
                {
                    result_frame = (Frame)return_value;
                    var next = new Attempt(owner, name + 1, frame, result_frame);
                    owner.known_attempts.AddLast(next);
                    if (result_frame.GetOrSubscribe(owner.names[name + 1], next.Consume))
                        return;
                    owner.task_master.Slot(owner);
                }
                else
                {
                    owner.task_master.ReportLookupError(owner, return_value.GetType());
                }
            }
        }
    }
}