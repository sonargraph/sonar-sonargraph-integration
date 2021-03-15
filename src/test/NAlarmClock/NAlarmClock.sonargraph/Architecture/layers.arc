artifact Model
{
    include "Model/**"
}

artifact View
{
    include "View/**"
    connect to Model
}

artifact Application
{
    include "NAlarmClock/**"
    connect to Model, View
}

artifact Foundation
{
    include "Foundation/**"
}
