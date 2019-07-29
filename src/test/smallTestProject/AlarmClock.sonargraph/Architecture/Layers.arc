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
    include "Application/**"
    connect to Model, View
}

artifact Foundation
{
    include "Foundation/**"
    connect to Model, View
}
